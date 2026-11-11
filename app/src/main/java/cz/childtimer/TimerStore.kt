package cz.childtimer

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.edit

data class Preferences(
    val presets: List<Int> = listOf(5, 10, 15, 30),
    val color: Int = 1,
    val sound: Boolean = true,
    val keepAwake: Boolean = true,
)

class TimerStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("child_timer", Context.MODE_PRIVATE)
    private fun bootCount() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    fun preferences() = Preferences(
        List(4) { prefs.getInt("preset_$it", listOf(5, 10, 15, 30)[it]).coerceIn(1, 180) },
        prefs.getInt("color", 1).coerceIn(0, 4),
        prefs.getBoolean("sound", true),
        prefs.getBoolean("awake", true),
    )

    fun save(settings: Preferences) {
        require(settings.presets.size == 4 && settings.presets.all { it in 1..180 })
        prefs.edit {
            settings.presets.forEachIndexed { index, value -> putInt("preset_$index", value) }
            putInt("color", settings.color)
            putBoolean("sound", settings.sound)
            putBoolean("awake", settings.keepAwake)
        }
    }

    fun countdown(): Countdown {
        val phase = runCatching { Phase.valueOf(prefs.getString("phase", "IDLE")!!) }.getOrDefault(Phase.IDLE)
        if (phase == Phase.RUNNING && prefs.getInt("boot", -2) != bootCount()) {
            // A monotonic deadline cannot survive a device reboot. Never resurrect a stale timer.
            return Countdown(Phase.FINISHED, prefs.getLong("total", 0))
        }
        return Countdown(phase, prefs.getLong("total", 0), prefs.getLong("remaining", 0), prefs.getLong("deadline", 0))
    }

    fun save(timer: Countdown) {
        // Persist transitions before scheduling an alarm, so a new process can restore it.
        prefs.edit(commit = true) {
            putString("phase", timer.phase.name)
            putLong("total", timer.totalMs)
            putLong("remaining", timer.remainingMs)
            putLong("deadline", timer.deadlineMs)
            putInt("boot", bootCount())
        }
    }

    fun finishIfDue(): Boolean {
        val timer = countdown()
        if (timer.phase != Phase.RUNNING || timer.remaining(SystemClock.elapsedRealtime()) > 0) return false
        save(timer.tick(SystemClock.elapsedRealtime()))
        return true
    }
}
