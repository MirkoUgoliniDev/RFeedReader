package it.mirkougolinidev.rfeedreader.core.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import it.mirkougolinidev.rfeedreader.RFeedReaderApplication
import it.mirkougolinidev.rfeedreader.core.common.UserPreferences
import it.mirkougolinidev.rfeedreader.core.download.OfflineFileVerifier
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OfflinePlaybackService : MediaSessionService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        player.setHandleAudioBecomingNoisy(true)
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId ?: return
                UserPreferences.get(this@OfflinePlaybackService).edit().putString(UserPreferences.LAST_EPISODE, id).apply()
                serviceScope.launch {
                    val episode = (application as RFeedReaderApplication).repository.getEpisode(id)
                    if (episode != null && episode.positionMs > 0 && player.currentMediaItem?.mediaId == id) {
                        player.seekTo(episode.positionMs)
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val id = player.currentMediaItem?.mediaId ?: return
                    serviceScope.launch { (application as RFeedReaderApplication).repository.setPlayed(id, true) }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                UserPreferences.get(this@OfflinePlaybackService).edit().putBoolean(UserPreferences.WAS_PLAYING, isPlaying).apply()
            }
        })
        session = MediaSession.Builder(this, player)
            .setCallback(OfflineSessionCallback())
            .build()
        restoreLastEpisode()
        serviceScope.launch {
            while (isActive) {
                delay(15_000)
                val id = player.currentMediaItem?.mediaId ?: continue
                val position = player.currentPosition
                withContext(Dispatchers.IO) {
                    (application as RFeedReaderApplication).repository.savePosition(id, position)
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onDestroy() {
        val id = player.currentMediaItem?.mediaId
        val position = player.currentPosition
        if (id != null) UserPreferences.get(this).edit().putString(UserPreferences.LAST_EPISODE, id).apply()
        if (id != null) CoroutineScope(Dispatchers.IO).launch {
            (application as RFeedReaderApplication).repository.savePosition(id, position)
        }
        serviceScope.cancel()
        session.release()
        player.release()
        super.onDestroy()
    }

    private fun restoreLastEpisode() {
        val preferences = UserPreferences.get(this)
        val id = preferences.getString(UserPreferences.LAST_EPISODE, null) ?: return
        serviceScope.launch {
            val episode = (application as RFeedReaderApplication).repository.getEpisode(id) ?: return@launch
            val canonical = OfflineFileVerifier.verifiedFile(File(filesDir, "episodes"), episode) ?: return@launch
            val item = MediaItem.Builder().setMediaId(episode.id).setUri(Uri.fromFile(canonical))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(episode.title).build()).build()
            player.setMediaItem(item)
            player.setPlaybackParameters(PlaybackParameters(preferences.getFloat(UserPreferences.PLAYBACK_SPEED, 1f)))
            player.prepare()
            player.seekTo(episode.positionMs)
            if (preferences.getBoolean(UserPreferences.WAS_PLAYING, false)) player.play()
        }
    }

    private inner class OfflineSessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            val future = SettableFuture.create<List<MediaItem>>()
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val repo = (application as RFeedReaderApplication).repository
                    // Gli episodi rimossi o non più verificati si scartano: prima bastava uno solo
                    // per rifiutare l'intera coda e non riprodurre niente.
                    val resolved = mediaItems.mapNotNull { requested ->
                        val episode = repo.getEpisode(requested.mediaId) ?: return@mapNotNull null
                        val file = OfflineFileVerifier.verifiedFile(File(filesDir, "episodes"), episode)
                            ?: return@mapNotNull null
                        MediaItem.Builder()
                            .setMediaId(episode.id)
                            .setUri(Uri.fromFile(file))
                            .setMediaMetadata(
                                MediaMetadata.Builder().setTitle(episode.title)
                                    .setArtist(repo.getPodcast(episode.podcastId)?.title.orEmpty()).build()
                            )
                            .build()
                    }
                    if (resolved.isEmpty()) error("Il file locale non è completo o verificato. Scarica prima l'episodio.")
                    future.set(resolved)
                } catch (error: Exception) {
                    future.setException(error)
                }
            }
            return future
        }
    }
}
