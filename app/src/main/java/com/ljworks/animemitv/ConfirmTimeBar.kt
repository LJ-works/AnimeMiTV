package com.ljworks.animemitv

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.KeyEvent
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.TimeBar
import java.util.concurrent.CopyOnWriteArraySet

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class ConfirmTimeBar(context: Context, attrs: AttributeSet?) : DefaultTimeBar(context, attrs) {
    private val listeners = CopyOnWriteArraySet<TimeBar.OnScrubListener>()
    private var waitingForConfirmation = false
    private var canceling = false

    init {
        super.addListener(object : TimeBar.OnScrubListener {
            override fun onScrubStart(timeBar: TimeBar, position: Long) =
                listeners.forEach { it.onScrubStart(timeBar, position) }

            override fun onScrubMove(timeBar: TimeBar, position: Long) =
                listeners.forEach { it.onScrubMove(timeBar, position) }

            override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                listeners.forEach { it.onScrubStop(timeBar, position, canceled || canceling) }
                waitingForConfirmation = false
                canceling = false
            }
        })
    }

    override fun addListener(listener: TimeBar.OnScrubListener) {
        listeners += listener
    }

    override fun removeListener(listener: TimeBar.OnScrubListener) {
        listeners -= listener
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            waitingForConfirmation = true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        canceling = waitingForConfirmation && !gainFocus
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
    }

    override fun postDelayed(action: Runnable, delayMillis: Long): Boolean =
        if (waitingForConfirmation) true else super.postDelayed(action, delayMillis)

    fun cancelPreview() {
        if (!waitingForConfirmation) return
        canceling = true
        super.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
    }
}
