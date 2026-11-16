package it.mirkougolinidev.rfeedreader.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import it.mirkougolinidev.rfeedreader.ui.theme.RFeedReaderTheme
import it.mirkougolinidev.rfeedreader.core.database.EpisodeEntity

@Composable
fun HomeScreen(
    episodes: List<EpisodeEntity> = emptyList(),
    podcastTitles: Map<String, String> = emptyMap(),
    onPlay: (EpisodeEntity) -> Unit = {},
    onBrowsePodcasts: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("RFeedReader", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("Pronti per la palestra", style = MaterialTheme.typography.headlineLarge)
            Text("La tua libreria, anche senza rete.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (episodes.isEmpty()) {
            EmptyStateCard(
                symbol = "♫",
                title = "Nessun episodio pronto",
                description = "Gli episodi scaricati e verificati saranno sempre disponibili qui, anche in modalità aereo."
            )
        } else {
            Text("La tua libreria offline", style = MaterialTheme.typography.titleLarge)
            episodes.take(8).forEach { episode ->
                EpisodeCard(
                    episode = episode,
                    podcastTitle = podcastTitles[episode.podcastId].orEmpty(),
                    onPlay = { onPlay(episode) }
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Inizia da un podcast", style = MaterialTheme.typography.titleLarge)
            Text(
                "Aggiungi un feed e scegli gli episodi da scaricare prima dell'allenamento.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onBrowsePodcasts, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Text("Vai ai podcast", style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    RFeedReaderTheme { HomeScreen() }
}
