package com.ljworks.animemitv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressRecorderTest {
    @Test
    fun playbackMustStartBeforeProgressIsSaved() {
        val saved = mutableListOf<EpisodeProgress>()
        val recorder = recorder(saved)

        recorder.stopped(10_000, 60_000)
        recorder.dispose(10_000, 60_000, isPlaying = false)

        assertTrue(saved.isEmpty())
    }

    @Test
    fun pausedSeekIsNotSavedWithoutResumingPlayback() {
        val saved = mutableListOf<EpisodeProgress>()
        val recorder = recorder(saved)

        recorder.playing(10_000, 60_000)
        recorder.stopped(12_000, 60_000)
        recorder.dispose(40_000, 60_000, isPlaying = false)

        assertEquals(EpisodeProgress(12_000, 60_000), saved.last())
    }

    @Test
    fun naturalEndKeepsWatchProgressAtTheBeginning() {
        val saved = mutableListOf<EpisodeProgress>()
        val recorder = recorder(saved)

        recorder.playing(59_000, 60_000)
        recorder.ended(60_000)
        recorder.stopped(60_000, 60_000)
        recorder.dispose(60_000, 60_000, isPlaying = false)

        assertEquals(EpisodeProgress(0, 60_000), saved.last())
    }

    private fun recorder(saved: MutableList<EpisodeProgress>) = PlaybackProgressRecorder { position, duration ->
        saved += EpisodeProgress(position, duration)
    }
}
