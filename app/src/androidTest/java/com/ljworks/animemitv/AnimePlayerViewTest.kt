package com.ljworks.animemitv

import android.view.LayoutInflater
import android.view.View
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.media3.ui.PlayerView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

@androidx.annotation.OptIn(UnstableApi::class)
class AnimePlayerViewTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun firstDirectionKeyShowsAutomaticallyHiddenControls() {
        lateinit var view: AnimePlayerView
        lateinit var player: ExoPlayer
        val playing = AtomicBoolean()
        composeRule.setContent {
            val context = LocalContext.current
            AndroidView(factory = {
                player = ExoPlayer.Builder(context).build().apply {
                    addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            playing.set(isPlaying)
                        }
                    })
                    setMediaSource(SilenceMediaSource.Factory().setDurationUs(60_000_000L).createMediaSource())
                    prepare()
                    playWhenReady = true
                }
                (LayoutInflater.from(context).inflate(R.layout.anime_player_view, null) as AnimePlayerView).apply {
                    this.player = player
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                        if (visibility != View.VISIBLE) {
                            findFocus()?.clearFocus()
                            requestFocus()
                        }
                    })
                    view = this
                }
            })
        }
        val controller = view.findViewById<View>(androidx.media3.ui.R.id.exo_controller)
        composeRule.waitUntil(2_000) { playing.get() }
        composeRule.runOnIdle { view.showController() }
        composeRule.waitUntil(2_000) { view.isControllerFullyVisible }
        composeRule.runOnIdle {
            view.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause).requestFocus()
        }
        composeRule.waitUntil(8_000) { controller.visibility == View.GONE }
        composeRule.waitUntil(1_000) { view.hasFocus() }
        composeRule.runOnIdle {
            assertFalse(view.isControllerFullyVisible)
            assertTrue("player must regain focus after controls hide", view.hasFocus())
        }

        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_UP)

        composeRule.waitUntil(1_000) { view.isControllerFullyVisible }
        composeRule.runOnIdle {
            assertTrue("first direction key should show hidden controls", view.isControllerFullyVisible)
            player.release()
        }
    }
}
