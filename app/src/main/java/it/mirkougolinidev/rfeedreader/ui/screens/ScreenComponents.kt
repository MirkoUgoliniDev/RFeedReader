package it.mirkougolinidev.rfeedreader.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.mirkougolinidev.rfeedreader.core.database.EpisodeEntity
import it.mirkougolinidev.rfeedreader.ui.player.formatTime

@Composable
internal fun ScreenHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun PodcastArtwork(url: String?, description: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
        Text("♫", fontSize = 28.sp, color = MaterialTheme.colorScheme.primary)
        if (!url.isNullOrBlank()) AsyncImage(
            model = url,
            contentDescription = description,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
internal fun EmptyStateCard(
    symbol: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = symbol,
                fontSize = 36.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { contentDescription = title }
            )
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun EpisodeCard(
    episode: EpisodeEntity,
    podcastTitle: String,
    onPlay: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                PodcastArtwork(episode.imageUrl, episode.title, Modifier.size(68.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(podcastTitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    Text(episode.title, style = MaterialTheme.typography.titleMedium, maxLines = 3)
                }
            }
            if (episode.publishedAt != null) Text(
                java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(episode.publishedAt)),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when (episode.downloadState) {
                "QUEUED" -> Text(episode.downloadError ?: "In attesa", style = MaterialTheme.typography.bodyLarge)
                "DOWNLOADING" -> {
                    // La dimensione vera arriva dal server (Content-Length): il length del feed RSS
                    // non è affidabile (Radio 24 dichiara 1000 byte per ogni episodio).
                    val total = episode.downloadTotalBytes?.takeIf { it > 0 }
                    Text(if (total != null) "Scaricati ${formatBytes(episode.downloadedBytes)} di ${formatBytes(total)}" else "Scaricati ${formatBytes(episode.downloadedBytes)}", style = MaterialTheme.typography.bodyMedium)
                    if (total != null) LinearProgressIndicator(
                        progress = { (episode.downloadedBytes.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    ) else LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                "FAILED" -> Text(
                    episode.downloadError?.let { "Download non riuscito: $it" } ?: "Download non riuscito · puoi riprovare",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error
                )
                "DOWNLOADED" -> Text("Disponibile offline · ${formatBytes(episode.downloadedBytes)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onPlay != null) Button(onClick = onPlay, modifier = Modifier.weight(1f).heightIn(min = 64.dp)) { Text("▶  Ascolta") }
                if (onDownload != null) Button(onClick = onDownload, modifier = Modifier.weight(1f).heightIn(min = 64.dp)) {
                    Text(if (episode.downloadState == "FAILED") "Riprova" else "Scarica")
                }
                if (onCancel != null) OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = 64.dp)) { Text("Annulla") }
                if (onDelete != null) OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f).heightIn(min = 64.dp)) { Text("Rimuovi") }
            }
        }
    }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.0f MB".format(bytes / (1024.0 * 1024))
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

