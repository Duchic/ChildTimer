package cz.childtimer

enum class Phase { IDLE, RUNNING, PAUSED, FINISHED }

/** Immutable countdown. elapsedRealtime is monotonic and includes device sleep. */
data class Countdown(
    val phase: Phase = Phase.IDLE,
    val totalMs: Long = 0,
    val remainingMs: Long = 0,
    val deadlineMs: Long = 0,
) {
    fun remaining(now: Long): Long =
        if (phase == Phase.RUNNING) (deadlineMs - now).coerceIn(0, totalMs) else remainingMs

    fun tick(now: Long): Countdown =
        if (phase == Phase.RUNNING && remaining(now) == 0L)
            copy(phase = Phase.FINISHED, remainingMs = 0, deadlineMs = 0) else this

    fun pause(now: Long): Countdown = when {
        phase != Phase.RUNNING -> this
        remaining(now) == 0L -> tick(now)
        else -> copy(phase = Phase.PAUSED, remainingMs = remaining(now), deadlineMs = 0)
    }

    fun resume(now: Long): Countdown = if (phase == Phase.PAUSED)
        copy(phase = Phase.RUNNING, deadlineMs = now + remainingMs) else this

    companion object {
        fun start(minutes: Int, now: Long): Countdown {
            require(minutes in 1..180)
            val duration = minutes * 60_000L
            return Countdown(Phase.RUNNING, duration, duration, now + duration)
        }
    }
}

fun formatRemaining(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0) + 999) / 1000
    return "%02d:%02d".format(java.util.Locale.ROOT, seconds / 60, seconds % 60)
}
