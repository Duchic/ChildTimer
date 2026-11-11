package cz.childtimer

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat

class TimerAlarm(private val context: Context) {
    private val manager = context.getSystemService(AlarmManager::class.java)
    private fun pending(deadline: Long = 0) = PendingIntent.getBroadcast(context, 1,
        Intent(context, TimerReceiver::class.java).putExtra("deadline", deadline),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun canScheduleExactly() = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()

    fun schedule(timer: Countdown) {
        cancel()
        if (timer.phase != Phase.RUNNING) return
        if (canScheduleExactly()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, timer.deadlineMs, pending(timer.deadlineMs))
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, timer.deadlineMs, pending(timer.deadlineMs))
        }
    }

    fun cancel() = manager.cancel(pending())
    fun dismiss() = context.getSystemService(NotificationManager::class.java).cancel(1)

    fun notifyFinished() {
        val notifications = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("timer_finished", "Dokončení časovače", NotificationManager.IMPORTANCE_HIGH)
                .apply { setSound(null, null); enableVibration(false) }
            notifications.createNotificationChannel(channel)
        }
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        notifications.notify(1, NotificationCompat.Builder(context, "timer_finished")
            .setSmallIcon(R.drawable.ic_timer).setContentTitle("Čas vypršel")
            .setContentText("Čas na další dobrodružství. Otevřete časovač.")
            // Sound is played separately so the app's sound toggle also works before channels.
            .setPriority(NotificationCompat.PRIORITY_HIGH).setSound(null).setVibrate(null).setDefaults(0)
            .setCategory(NotificationCompat.CATEGORY_ALARM).setContentIntent(open).setAutoCancel(true).build())
    }
}

/** A short chime, even if notification permission was declined. No looping alarm. */
fun playChime(onFinished: () -> Unit = {}) {
    val tone = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 65) }.getOrNull()
    if (tone == null) { onFinished(); return }
    tone.startTone(ToneGenerator.TONE_PROP_ACK, 900)
    Handler(Looper.getMainLooper()).postDelayed({ tone.release(); onFinished() }, 1200)
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = TimerStore(context)
        val timer = store.countdown()
        if (timer.phase != Phase.RUNNING || intent.getLongExtra("deadline", -1) != timer.deadlineMs) return
        if (store.finishIfDue()) {
            TimerAlarm(context).notifyFinished()
            if (store.preferences().sound) {
                val pending = goAsync()
                playChime { pending.finish() }
            }
        }
    }
}
