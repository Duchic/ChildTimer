package cz.childtimer

import org.junit.Assert.*
import org.junit.Test

class CountdownTest {
    @Test fun `time includes background elapsed time`() {
        val timer = Countdown.start(15, 1000)
        assertEquals(450000L, timer.remaining(451000))
        assertEquals(Phase.FINISHED, timer.tick(901000).phase)
        assertEquals(0L, timer.remaining(9999999))
    }

    @Test fun `pause freezes remaining duration and resume establishes a new deadline`() {
        val paused = Countdown.start(5, 1000).pause(61000)
        assertEquals(Phase.PAUSED, paused.phase)
        assertEquals(240000L, paused.remaining(999999))
        val resumed = paused.resume(1000000)
        assertEquals(120000L, resumed.remaining(1120000))
        assertEquals(Phase.FINISHED, resumed.tick(1240000).phase)
    }

    @Test fun `pause at deadline finishes instead of creating empty paused timer`() {
        assertEquals(Phase.FINISHED, Countdown.start(1, 0).pause(60000).phase)
    }

    @Test fun `repeated pause and resume cannot reset deadline`() {
        val timer = Countdown.start(5, 0)
        assertEquals(timer, timer.resume(120000))
        val paused = timer.pause(100000)
        assertEquals(paused, paused.pause(200000))
    }

    @Test fun `display rounds up until deadline and supports long durations`() {
        assertEquals("00:01", formatRemaining(1))
        assertEquals("01:00", formatRemaining(59999))
        assertEquals("00:00", formatRemaining(-1))
        assertEquals("180:00", formatRemaining(10800000))
    }

    @Test fun `invalid durations are rejected`() {
        listOf(0, -1, 181, Int.MAX_VALUE).forEach { minutes ->
            assertThrows(IllegalArgumentException::class.java) { Countdown.start(minutes, 0) }
        }
    }
}
