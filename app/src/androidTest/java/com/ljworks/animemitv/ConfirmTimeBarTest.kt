package com.ljworks.animemitv

import android.view.KeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.TimeBar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ConfirmTimeBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun directionKeysPreviewUntilConfirmation() {
        val stops = exercise { bar, stops ->
            bar.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, key(KeyEvent.KEYCODE_DPAD_RIGHT))
            assertEquals(emptyList<Stop>(), stops)
            bar.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER, key(KeyEvent.KEYCODE_DPAD_CENTER))
        }

        assertEquals(listOf(Stop(15_000L, false)), stops)
    }

    @Test
    fun cancelPreviewDoesNotConfirmSeek() {
        val stops = exercise { bar, _ ->
            bar.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, key(KeyEvent.KEYCODE_DPAD_RIGHT))
            bar.cancelPreview()
        }

        assertEquals(listOf(Stop(15_000L, true)), stops)
    }

    private fun exercise(block: (ConfirmTimeBar, MutableList<Stop>) -> Unit): List<Stop> {
        lateinit var bar: ConfirmTimeBar
        val stops = mutableListOf<Stop>()
        composeRule.setContent {
            val context = LocalContext.current
            AndroidView(factory = {
                ConfirmTimeBar(context, null).also {
                    bar = it
                    it.setDuration(60_000L)
                    it.setPosition(0L)
                    it.setKeyTimeIncrement(15_000L)
                    it.addListener(object : TimeBar.OnScrubListener {
                        override fun onScrubStart(timeBar: TimeBar, position: Long) = Unit
                        override fun onScrubMove(timeBar: TimeBar, position: Long) = Unit
                        override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                            stops += Stop(position, canceled)
                        }
                    })
                }
            })
        }
        composeRule.runOnIdle { block(bar, stops) }
        return stops
    }

    private fun key(code: Int) = KeyEvent(KeyEvent.ACTION_DOWN, code)

    private data class Stop(val position: Long, val canceled: Boolean)
}
