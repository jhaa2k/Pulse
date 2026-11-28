package com.vexo.studio
import com.vexo.studio.playback.PlaybackClock
import org.junit.Assert.*
import org.junit.Test
class PlaybackClockTest{
 @Test fun countdownRoundsUp(){
    assertEquals(60L,PlaybackClock.remainingSeconds(61000,1000))
    assertEquals(1L,PlaybackClock.remainingSeconds(61000,60999))
    assertEquals(0L,PlaybackClock.remainingSeconds(61000,61000))
    assertEquals(0L,PlaybackClock.remainingSeconds(61000,62000))
 }
}
