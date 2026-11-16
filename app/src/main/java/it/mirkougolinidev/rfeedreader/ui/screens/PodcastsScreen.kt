package it.mirkougolinidev.rfeedreader.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import it.mirkougolinidev.rfeedreader.RFeedReaderApplication
import it.mirkougolinidev.rfeedreader.core.database.PodcastEntity
import kotlinx.coroutines.launch
import it.mirkougolinidev.rfeedreader.ui.theme.RFeedReaderTheme

@Composable
fun PodcastsScreen(onPodcastClick: (PodcastEntity) -> Unit = {}) {
    val context = LocalContext.current
    val repository = (context.applicationContext as RFeedReaderApplication).repository
    val podcasts by repository.podcasts.collectAsStateWithLifecycle(emptyList())
    val unplayed by repository.unplayedCounts.collectAsStateWithLifecycle(emptyMap())
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var feedUrl by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        ScreenHeader("Podcast", "Le tue sottoscrizioni")
        Button(onClick = { showAdd = true; error = null }, modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text("＋  Aggiungi podcast", style = MaterialTheme.typography.titleMedium)
        }
        if (podcasts.isEmpty()) EmptyStateCard(
            symbol = "◉", title = "Ancora nessun podcast",
            description = "Aggiungi l'indirizzo di un feed RSS o Atom per trovare e scaricare gli episodi."
        ) else podcasts.forEach { podcast ->
            Card(
                onClick = { onPodcastClick(podcast) }, modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    PodcastArtwork(podcast.imageUrl, "Copertina ${podcast.title}", Modifier.size(82.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(podcast.title, style = MaterialTheme.typography.titleLarge)
                        val unread = unplayed[podcast.id] ?: 0
                        Text("$unread episodi non ascoltati", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        Text(podcast.description.ifBlank { podcast.feedUrl }, style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                    }
                }
            }
        }
    }
    if (showAdd) AlertDialog(
        onDismissRequest = { if (!busy) showAdd = false },
        title = { Text("Aggiungi podcast") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Incolla l'indirizzo del feed RSS o Atom.", style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(value = feedUrl, onValueChange = { feedUrl = it; error = null },
                    label = { Text("URL del feed") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (busy) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && feedUrl.isNotBlank(), onClick = {
                busy = true; error = null
                scope.launch {
                    runCatching { repository.addFeed(feedUrl) }
                        .onSuccess { showAdd = false; feedUrl = "" }
                        .onFailure { error = it.message ?: "Impossibile aggiungere il feed." }
                    busy = false
                }
            }) { Text(if (busy) "Lettura…" else "Aggiungi") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { showAdd = false }) { Text("Annulla") } }
    )
}

