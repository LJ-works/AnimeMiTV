package com.ljworks.animemitv

import android.content.Context

interface FollowedAnimeStore {
    fun load(): Set<Int>

    fun save(ids: Set<Int>): Boolean
}

internal object EmptyFollowedAnimeStore : FollowedAnimeStore {
    override fun load(): Set<Int> = emptySet()

    override fun save(ids: Set<Int>): Boolean = true
}

class SharedPreferencesFollowedAnimeStore(context: Context) : FollowedAnimeStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun load(): Set<Int> = preferences.getStringSet(KEY_IDS, emptySet())
        .orEmpty()
        .mapNotNull(String::toIntOrNull)
        .toSet()

    override fun save(ids: Set<Int>): Boolean = preferences.edit()
        .putStringSet(KEY_IDS, ids.map(Int::toString).toSet())
        .commit()

    private companion object {
        const val PREFERENCES = "followed_anime"
        const val KEY_IDS = "ids"
    }
}
