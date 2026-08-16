package com.ljworks.animemitv

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeProgressStoreTest {
    @Test
    fun progressSurvivesCreatingANewStore() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("episode_progress", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()

        SharedPreferencesEpisodeProgressStore(context).save("episode-1", EpisodeProgress(30_000, 60_000))

        assertEquals(
            EpisodeProgress(30_000, 60_000),
            SharedPreferencesEpisodeProgressStore(context).load()["episode-1"],
        )
        preferences.edit().clear().commit()
    }
}
