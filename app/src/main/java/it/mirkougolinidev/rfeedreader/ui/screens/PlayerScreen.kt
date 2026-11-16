package it.mirkougolinidev.rfeedreader.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackParameters
import androidx.media3.session.MediaController
import it.mirkougolinidev.rfeedreader.RFeedReaderApplication
import it.mirkougolinidev.rfeedreader.core.common.UserPreferences
import it.mirkougolinidev.rfeedreader.core.database.EpisodeEntity
import it.mirkougolinidev.rfeedreader.ui.player.PlaybackSnapshot
import it.mirkougolinidev.rfeedreader.ui.player.formatTime
import it.mirkougolinidev.rfeedreader.ui.player.playDownloadedEpisode
import it.mirkougolinidev.rfeedreader.ui.theme.RFeedReaderTheme

@Composable
fun PlayerScreen(
    controller: MediaController?,
    snapshot: PlaybackSnapshot,
    episodes: List<EpisodeEntity>,
    podcastTitles: Map<String, String>,
    remainingMinutes: Int,
    onSetTimer: (Int) -> Unit
) {
    val context = LocalContext.current
    val preferences = remember { UserPreferences.get(context) }
    val episode = episodes.firstOrNull { it.id == snapshot.episodeId }
    val queue = episodes.filter { it.downloadState == "DOWNLOADED" && it.localPath != null }
    var seeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableFloatStateOf(0f) }
    var showQueue by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(snapshot.speed) }

    LaunchedEffect(snapshot.speed) { speed = snapshot.speed }
    LaunchedEffect(snapshot.positionMs, snapshot.durationMs) {
        if (!seeking && snapshot.durationMs > 0) seekFraction = (snapshot.positionMs.toFloat() / snapshot.durationMs).coerceIn(0f, 1f)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("IN RIPRODUZIONE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                PodcastArtwork(episode?.imageUrl, episode?.title ?: "Copertina podcast", Modifier.size(220.dp))
                Text(podcastTitles[episode?.podcastId].orEmpty(), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
        }
        Text(episode?.title ?: snapshot.title.ifBlank { "Scegli un episodio scaricato" }, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        if (snapshot.isBuffering) Text("Caricamento audio…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        snapshot.error?.let { Text("Problema di riproduzione: $it", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error) }

        Column(Modifier.fillMaxWidth()) {
            Slider(
                value = seekFraction,
                onValueChange = { seeking = true; seekFraction = it },
                onValueChangeFinished = {
                    if (snapshot.durationMs > 0) controller?.seekTo((seekFraction * snapshot.durationMs).toLong())
                    seeking = false
                },
                enabled = snapshot.durationMs > 0,
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(if (seeking) (seekFraction * snapshot.durationMs).toLong() else snapshot.positionMs), style = MaterialTheme.typography.bodyMedium)
                Text("−${formatTime((snapshot.durationMs - snapshot.positionMs).coerceAtLeast(0))}", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                val seconds = preferences.getInt(UserPreferences.SKIP_BACK, 30)
                controller?.seekTo((snapshot.positionMs - seconds * 1000L).coerceAtLeast(0))
            }, modifier = Modifier.weight(1f).heightIn(min = 72.dp)) { Text("↶\n${preferences.getInt(UserPreferences.SKIP_BACK, 30)} s", textAlign = TextAlign.Center) }
            Button(onClick = { if (snapshot.isPlaying) controller?.pause() else controller?.play() },
                modifier = Modifier.weight(1.4f).heightIn(min = 84.dp)) {
                Text(if (snapshot.isPlaying) "❚❚  Pausa" else "▶  Riproduci", style = MaterialTheme.typography.titleLarge)
            }
            OutlinedButton(onClick = {
                val seconds = preferences.getInt(UserPreferences.SKIP_FORWARD, 30)
                controller?.seekTo((snapshot.positionMs + seconds * 1000L).coerceAtMost(snapshot.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE))
            }, modifier = Modifier.weight(1f).heightIn(min = 72.dp)) { Text("${preferences.getInt(UserPreferences.SKIP_FORWARD, 30)} s\n↷", textAlign = TextAlign.Center) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Velocità", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { speedMenu = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) { Text("%.2gx".format(speed), style = MaterialTheme.typography.titleLarge) }
                androidx.compose.material3.DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                    listOf(0.8f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f).forEach { value ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("%.2gx".format(value)) }, onClick = {
                                speed = value
                                controller?.setPlaybackParameters(PlaybackParameters(value))
                                preferences.edit().putFloat(UserPreferences.PLAYBACK_SPEED, value).apply()
                                speedMenu = false
                            }
                        )
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text("Timer", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { showTimer = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
                    Text(if (remainingMinutes > 0) "$remainingMinutes min" else "Imposta", style = MaterialTheme.typography.titleLarge)
                }
            }
            Column(Modifier.weight(1f)) {
                Text("Coda", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { showQueue = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) { Text("${queue.size} episodi") }
            }
        }
    }

    if (showTimer) AlertDialog(
        onDismissRequest = { showTimer = false }, title = { Text("Timer spegnimento") },
        text = { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(15, 30, 45, 60).forEach { minutes -> TextButton(onClick = { onSetTimer(minutes); showTimer = false }) { Text("$minutes min") }
        } } },
        confirmButton = { TextButton(onClick = { onSetTimer(0); showTimer = false }) { Text("Disattiva") } }
    )
    if (showQueue) AlertDialog(
        onDismissRequest = { showQueue = false }, title = { Text("Coda offline") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (queue.isEmpty()) Text("Scarica episodi per creare la coda.")
            queue.forEach { item ->
                TextButton(onClick = { playDownloadedEpisode(controller, queue, item); showQueue = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("${if (item.id == snapshot.episodeId) "♫  " else ""}${item.title}", maxLines = 2)
                }
            }
        } },
        confirmButton = { TextButton(onClick = { showQueue = false }) { Text("Chiudi") } }
    )
}

@Preview(showBackground = true)
@Composable
private fun PlayerPreview() {
    RFeedReaderTheme { Text("Player", modifier = Modifier.padding(24.dp)) }
}
