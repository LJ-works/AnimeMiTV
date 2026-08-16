package com.ljworks.animemitv

import android.content.Context
import androidx.core.content.edit

data class EpisodeProgress(
    val positionMillis: Long,
    val durationMillis: Long,
)

interface EpisodeProgressStore {
    fun load(): Map<String, EpisodeProgress>

    fun save(episodeId: String, progress: EpisodeProgress)
}

internal object EmptyEpisodeProgressStore : EpisodeProgressStore {
    override fun load(): Map<String, EpisodeProgress> = emptyMap()

    override fun save(episodeId: String, progress: EpisodeProgress) = Unit
}

class SharedPreferencesEpisodeProgressStore(context: Context) : EpisodeProgressStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun load(): Map<String, EpisodeProgress> = preferences.all.mapNotNull { (episodeId, value) ->
        val parts = (value as? String)?.split(',', limit = 2) ?: return@mapNotNull null
        val position = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        val duration = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
        episodeId to EpisodeProgress(position.coerceAtLeast(0), duration.coerceAtLeast(0))
    }.toMap()

    override fun save(episodeId: String, progress: EpisodeProgress) {
        preferences.edit { putString(episodeId, "${progress.positionMillis},${progress.durationMillis}") }
    }

    private companion object {
        const val PREFERENCES = "episode_progress"
    }
}
