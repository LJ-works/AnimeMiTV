package com.ljworks.animemitv

import org.junit.Assert.assertEquals
import org.junit.Test

class AnimePlayerTest {
    @Test
    fun seekPositionClampsToTheMediaBounds() {
        assertEquals(0L, seekPosition(5_000L, 60_000L, -10_000L))
        assertEquals(60_000L, seekPosition(55_000L, 60_000L, 10_000L))
        assertEquals(70_000L, seekPosition(60_000L, -1L, 10_000L))
    }
}
