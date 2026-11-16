package it.mirkougolinidev.rfeedreader.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.mirkougolinidev.rfeedreader.RFeedReaderApplication
import it.mirkougolinidev.rfeedreader.core.database.EpisodeEntity
import it.mirkougolinidev.rfeedreader.ui.theme.RFeedReaderTheme
import kotlinx.coroutines.launch

@Composable
fun DownloadsScreen(onPlay: (EpisodeEntity) -> Unit = {}, onRemoveEpisode: (EpisodeEntity) -> Unit = {}) {
    val context = LocalContext.current
    val application = context.applicationContext as RFeedReaderApplication
    val repository = application.repository
    val scheduler = application.downloadScheduler
    // Solo le righe con un download, non tutti gli episodi di tutti i podcast.
    val episodes by repository.downloadEpisodes.collectAsStateWithLifecycle(emptyList())
    val verifiedIds by repository.verifiedEpisodeIds.collectAsStateWithLifecycle(emptySet())
    val podcasts by repository.podcasts.collectAsStateWithLifecycle(emptyList())
    val downloadedBytes by repository.downloadedBytes.collectAsStateWithLifecycle(0L)
    val scope = rememberCoroutineScope()
    val titles = remember(podcasts) { podcasts.associate { it.id to it.title } }
    // In corso e in coda in cima, poi i completati dal più recente.
    val sorted = remember(episodes) {
        episodes.sortedWith(compareBy<EpisodeEntity> { stateOrder(it.downloadState) }.thenByDescending { it.publishedAt ?: 0L })
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item(key = "header") { ScreenHeader("Download", "Preparali prima di uscire") }
        item(key = "space") { Text("Spazio occupato  ·  ${formatBytes(downloadedBytes)}", style = MaterialTheme.typography.titleMedium) }
        if (sorted.isEmpty()) item(key = "empty") {
            EmptyStateCard(
                symbol = "↓", title = "Nessun download",
                description = "Dalla pagina di un podcast puoi scaricare episodi per ascoltarli senza connessione."
            )
        } else items(sorted, key = { it.id }) { episode ->
            val verified = episode.id in verifiedIds
            val pending = episode.downloadState == "QUEUED" || episode.downloadState == "DOWNLOADING"
            EpisodeCard(
                episode = episode,
                podcastTitle = titles[episode.podcastId].orEmpty(),
                onPlay = if (verified) ({ onPlay(episode) }) else null,
                onDownload = if (episode.downloadState == "FAILED" || episode.downloadState == "DOWNLOADED" && !verified) ({ scope.launch { runCatching { scheduler.enqueue(episode.id) } } }) else null,
                onCancel = if (pending) ({ scope.launch { runCatching { scheduler.cancel(episode.id) } } }) else null,
                onDelete = if (episode.downloadState == "DOWNLOADED") ({ onRemoveEpisode(episode) }) else null
            )
        }
    }
}

private fun stateOrder(state: String) = when (state) {
    "DOWNLOADING" -> 0
    "QUEUED" -> 1
    "FAILED" -> 2
    else -> 3
}

@Preview(showBackground = true)
@Composable
private fun DownloadsScreenPreview() {
    RFeedReaderTheme { EmptyStateCard("↓", "Nessun download", "I download appariranno qui.") }
}
