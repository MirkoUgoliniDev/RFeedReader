package it.mirkougolinidev.rfeedreader.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.mirkougolinidev.rfeedreader.RFeedReaderApplication
import it.mirkougolinidev.rfeedreader.core.database.EpisodeEntity
import kotlinx.coroutines.launch

@Composable
fun PodcastDetailScreen(
    podcastId: String,
    onPlay: (EpisodeEntity) -> Unit,
    onRemoveEpisode: (EpisodeEntity) -> Unit,
    onBeforeRemove: (String) -> Unit,
    onRemoved: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as RFeedReaderApplication
    val repository = application.repository
    val scheduler = application.downloadScheduler
    val podcasts by repository.podcasts.collectAsStateWithLifecycle(emptyList())
    val podcast = podcasts.firstOrNull { it.id == podcastId }
    val episodes by remember(podcastId) { repository.podcastEpisodes(podcastId) }.collectAsStateWithLifecycle(emptyList())
    val verifiedIds by repository.verifiedEpisodeIds.collectAsStateWithLifecycle(emptySet())
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }

    // LazyColumn: con centinaia di episodi si compongono solo le schede visibili.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item(key = "header") { ScreenHeader(podcast?.title ?: "Podcast", "Episodi più recenti") }
        if (podcast != null) {
            item(key = "description") {
                Text(podcast.description.ifBlank { podcast.feedUrl }, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item(key = "actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(enabled = !refreshing, onClick = {
                        refreshing = true; error = null
                        scope.launch {
                            runCatching { repository.refresh(podcastId) }.onFailure { error = it.message ?: "Aggiornamento non riuscito." }
                            refreshing = false
                        }
                    }, modifier = Modifier.weight(1f).heightIn(min = 60.dp)) { Text(if (refreshing) "Aggiornamento…" else "Aggiorna feed") }
                    OutlinedButton(onClick = { confirmRemove = true }, modifier = Modifier.weight(1f).heightIn(min = 60.dp)) { Text("Rimuovi") }
                }
            }
            error?.let { message ->
                item(key = "error") { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge) }
            }
        }
        if (episodes.isEmpty()) item(key = "empty") {
            EmptyStateCard("♫", "Nessun episodio", "Aggiorna il feed per cercare nuovi episodi.")
        } else items(episodes, key = { it.id }) { episode ->
            val verified = episode.id in verifiedIds
            val pending = episode.downloadState == "QUEUED" || episode.downloadState == "DOWNLOADING"
            EpisodeCard(
                episode = episode,
                podcastTitle = podcast?.title.orEmpty(),
                onPlay = if (verified) ({ onPlay(episode) }) else null,
                onDownload = if (verified || pending) null else ({
                    scope.launch { runCatching { scheduler.enqueue(episode.id) }.onFailure { error = it.message } }
                }),
                onCancel = if (pending) ({ scope.launch { runCatching { scheduler.cancel(episode.id) } } }) else null,
                onDelete = if (episode.downloadState == "DOWNLOADED") ({ onRemoveEpisode(episode) }) else null
            )
        }
    }

    if (confirmRemove && podcast != null) AlertDialog(
        onDismissRequest = { confirmRemove = false },
        title = { Text("Rimuovere ${podcast.title}?") },
        text = { Text("La sottoscrizione e gli episodi scaricati associati saranno rimossi dal dispositivo.") },
        confirmButton = {
            TextButton(onClick = {
                confirmRemove = false
                scope.launch {
                    onBeforeRemove(podcastId)
                    // Annullamento dei download e rimozione avvengono insieme e arrivano in fondo
                    // anche se la schermata viene chiusa a metà.
                    runCatching { scheduler.removePodcast(podcastId) }
                    onRemoved()
                }
            }) { Text("Rimuovi podcast") }
        },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Annulla") } }
    )
}
