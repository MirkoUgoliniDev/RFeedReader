package it.mirkougolinidev.rfeedreader.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "podcasts", indices = [Index(value = ["feedUrl"], unique = true)])
data class PodcastEntity(
    @PrimaryKey val id: String,
    val feedUrl: String,
    val finalFeedUrl: String,
    val title: String,
    val description: String,
    val imageUrl: String?,
    val websiteUrl: String?,
    val lastUpdated: Long,
    val lastError: String? = null
)

/** Conteggio degli episodi non ascoltati per podcast (proiezione leggera, non un'entità). */
data class UnplayedCount(val podcastId: String, val unplayed: Int)

/** Chi possiede un file in filesDir/episodes: un download completato (path) o in corso (token). */
data class FileOwner(val localPath: String?, val downloadToken: String?)

@Entity(
    tableName = "episodes",
    indices = [Index(value = ["podcastId"]), Index(value = ["podcastId", "stableKey"], unique = true)]
)
data class EpisodeEntity(
    @PrimaryKey val id: String,
    val podcastId: String,
    val stableKey: String,
    val guid: String,
    val title: String,
    val description: String,
    val enclosureUrl: String,
    val mimeType: String?,
    val byteLength: Long?,
    val publishedAt: Long?,
    val durationMs: Long?,
    val imageUrl: String?,
    val isPlayed: Boolean = false,
    val positionMs: Long = 0,
    val downloadState: String = "NOT_DOWNLOADED",
    val localPath: String? = null,
    val downloadedBytes: Long = 0,
    /**
     * Proprietario del download in corso. Ogni accodamento genera un token nuovo: solo il worker
     * che lo possiede può aggiornare la riga, così un worker vecchio (annullato ma ancora bloccato
     * in rete) non può sovrascrivere lo stato di quello nuovo.
     */
    val downloadToken: String? = null,
    /** Dimensione reale dal Content-Length HTTP; null se il server non la comunica. */
    val downloadTotalBytes: Long? = null,
    /** Ultimo errore di download, da mostrare all'utente (anche durante i nuovi tentativi automatici). */
    val downloadError: String? = null
)
