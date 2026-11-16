package it.mirkougolinidev.rfeedreader.ui.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import it.mirkougolinidev.rfeedreader.core.player.OfflinePlaybackService
import it.mirkougolinidev.rfeedreader.core.database.EpisodeEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.runtime.LaunchedEffect

data class PlaybackSnapshot(
    val episodeId: String? = null,
    val title: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    val error: String? = null
)

@Composable
fun rememberMediaController(): MediaController? {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    androidx.compose.runtime.DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, OfflinePlaybackService::class.java))
        val future: ListenableFuture<MediaController> = MediaController.Builder(context, token).buildAsync()
        future.addListener({ controller = runCatching { future.get() }.getOrNull() }, ContextCompat.getMainExecutor(context))
        onDispose {
            controller = null
            MediaController.releaseFuture(future)
        }
    }
    return controller
}

@Composable
fun rememberPlaybackSnapshot(controller: MediaController?): PlaybackSnapshot = rememberPlaybackSnapshotState(controller).value

/**
 * Restituisce lo State e non il valore: la posizione cambia ogni 500 ms durante l'ascolto e
 * leggere il valore nella radice faceva ricomporre tutta l'app (e ricreare il grafo di navigazione).
 */
@Composable
fun rememberPlaybackSnapshotState(controller: MediaController?): State<PlaybackSnapshot> {
    val state = remember(controller) { mutableStateOf(PlaybackSnapshot()) }
    var snapshot by state
    LaunchedEffect(controller) {
        if (controller == null) {
            snapshot = PlaybackSnapshot()
            return@LaunchedEffect
        }
        while (isActive) {
            val item = controller.currentMediaItem
            snapshot = PlaybackSnapshot(
                episodeId = item?.mediaId?.takeIf(String::isNotBlank),
                title = item?.mediaMetadata?.title?.toString().orEmpty(),
                isPlaying = controller.isPlaying,
                isBuffering = controller.playbackState == androidx.media3.common.Player.STATE_BUFFERING,
                positionMs = controller.currentPosition.coerceAtLeast(0),
                durationMs = controller.duration.takeIf { it > 0 } ?: 0,
                speed = controller.playbackParameters.speed,
                error = controller.playerError?.message
            )
            delay(500)
        }
    }
    return state
}

fun playDownloadedEpisode(controller: MediaController?, queue: List<EpisodeEntity>, episode: EpisodeEntity) {
    if (controller == null) return
    val playable = queue.filter { it.downloadState == "DOWNLOADED" && it.localPath != null }
    val index = playable.indexOfFirst { it.id == episode.id }.takeIf { it >= 0 } ?: 0
    controller.setMediaItems(playable.map { MediaItem.Builder().setMediaId(it.id).build() }, index, episode.positionMs)
    controller.prepare()
    controller.play()
}

fun formatTime(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainder = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remainder) else "%d:%02d".format(minutes, remainder)
}
