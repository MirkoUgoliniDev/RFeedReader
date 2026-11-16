package it.mirkougolinidev.rfeedreader.core.common

import android.content.Context

object UserPreferences {
    const val FILE = "user_preferences"
    const val WIFI_ONLY = "wifi_only"
    const val SKIP_BACK = "skip_back_seconds"
    const val SKIP_FORWARD = "skip_forward_seconds"
    const val PLAYBACK_SPEED = "playback_speed"
    const val LAST_EPISODE = "last_episode_id"
    const val WAS_PLAYING = "was_playing"
    const val AUTOMATIC_REFRESH = "automatic_refresh"
    const val SEEDED_FEEDS = "seeded_default_feeds"
    fun get(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
