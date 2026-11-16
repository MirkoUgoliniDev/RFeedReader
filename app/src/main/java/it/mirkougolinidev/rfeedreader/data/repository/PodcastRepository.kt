package it.mirkougolinidev.rfeedreader.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import it.mirkougolinidev.rfeedreader.core.database.EpisodeEntity
import it.mirkougolinidev.rfeedreader.core.database.PodcastEntity
import it.mirkougolinidev.rfeedreader.core.database.RFeedDatabase
import it.mirkougolinidev.rfeedreader.core.network.FeedException
import it.mirkougolinidev.rfeedreader.core.network.FeedParser
import it.mirkougolinidev.rfeedreader.core.network.ParsedFeed
import it.mirkougolinidev.rfeedreader.core.download.OfflineFileVerifier
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class PodcastRepository(private val context: Context, private val database: RFeedDatabase, private val parser: FeedParser) {
    private val dao = database.podcastDao()

    val podcasts: Flow<List<PodcastEntity>> = dao.observePodcasts()
    val episodes: Flow<List<EpisodeEntity>> = dao.observeAllEpisodes()
    val playableEpisodes: Flow<List<EpisodeEntity>> = dao.observePlayableEpisodes().map { episodes ->
        episodes.filter(::isVerifiedLocal)
    }.flowOn(Dispatchers.IO)
    val downloadedBytes: Flow<Long> = dao.observeDownloadedBytes().distinctUntilChanged()
    val downloadEpisodes: Flow<List<EpisodeEntity>> = dao.observeDownloadEpisodes()
    /** Id degli episodi riproducibili: la verifica dei file avviene su IO, mai durante la composizione. */
    val verifiedEpisodeIds: Flow<Set<String>> = playableEpisodes.map { list -> list.mapTo(HashSet()) { it.id } as Set<String> }
        .distinctUntilChanged()
    val unplayedCounts: Flow<Map<String, Int>> = dao.observeUnplayedCounts()
        .map { rows -> rows.associate { it.podcastId to it.unplayed } }
        .distinctUntilChanged()

    val episodesDirectory: File get() = File(context.filesDir, "episodes")

    suspend fun addFeed(rawUrl: String): PodcastEntity = withContext(Dispatchers.IO) {
        val canonical = canonicalUrl(rawUrl)
        val (finalUrl, parsed) = parser.fetchAndParse(canonical)
        saveFeed(canonical, finalUrl, parsed)
    }

    suspend fun addFeedIfMissing(rawUrl: String) = withContext(Dispatchers.IO) {
        val canonical = canonicalUrl(rawUrl)
        if (dao.findPodcastByUrl(canonical) == null) {
            val (finalUrl, parsed) = parser.fetchAndParse(canonical)
            saveFeed(canonical, finalUrl, parsed)
        }
    }

    suspend fun refreshFeed(podcastId: String): PodcastEntity {
        val existing = dao.getPodcast(podcastId) ?: error("Podcast non trovato.")
        return try {
            val (finalUrl, parsed) = parser.fetchAndParse(existing.feedUrl)
            saveFeed(existing.feedUrl, finalUrl, parsed, existing.id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // UPDATE mirato e non REPLACE dell'istantanea letta prima: non riporta in vita un
            // podcast rimosso nel frattempo e non sovrascrive un aggiornamento riuscito in parallelo.
            withContext(NonCancellable) { dao.setLastError(podcastId, error.message ?: "Aggiornamento non riuscito.") }
            throw error
        }
    }

    /**
     * L'id del podcast si decide dentro la transazione: due inserimenti concorrenti dello stesso
     * indirizzo (seeding all'avvio e aggiunta manuale) convergono sullo stesso id invece di
     * sostituirsi a vicenda lasciando episodi orfani. Un refresh di un podcast rimosso nel
     * frattempo non lo ricrea.
     */
    private suspend fun saveFeed(feedUrl: String, finalUrl: String, parsed: ParsedFeed, knownId: String? = null): PodcastEntity =
        database.withTransaction {
            val id = if (knownId != null) {
                dao.getPodcast(knownId)?.id ?: throw FeedException("Il podcast è stato rimosso.")
            } else {
                dao.findPodcastByUrl(feedUrl)?.id ?: UUID.randomUUID().toString()
            }
            val podcast = PodcastEntity(
                id = id,
                feedUrl = feedUrl,
                finalFeedUrl = finalUrl,
                title = parsed.title,
                description = parsed.description,
                imageUrl = parsed.imageUrl,
                websiteUrl = parsed.websiteUrl,
                lastUpdated = System.currentTimeMillis()
            )
            val episodes = parsed.episodes.map { item ->
                EpisodeEntity(
                    id = "$id:${item.stableKey}", podcastId = id, stableKey = item.stableKey,
                    guid = item.guid, title = item.title, description = item.description,
                    enclosureUrl = item.enclosureUrl, mimeType = item.mimeType, byteLength = item.byteLength,
                    publishedAt = item.publishedAt, durationMs = item.durationMs, imageUrl = item.imageUrl ?: parsed.imageUrl
                )
            }
            dao.saveFeed(podcast, episodes)
            podcast
        }

    /**
     * Righe cancellate in una transazione (un refresh concorrente non può reinserire episodi
     * orfani in mezzo), file cancellati solo dopo il commit (un kill a metà non lascia righe
     * DOWNLOADED senza file; i file rimasti li pulisce il riallineamento all'avvio).
     */
    suspend fun removePodcast(id: String) = withContext(Dispatchers.IO) {
        val paths = database.withTransaction {
            val paths = dao.getEpisodes(id).mapNotNull { it.localPath }
            dao.deleteEpisodes(id)
            dao.deletePodcast(id)
            paths
        }
        paths.forEach { runCatching { File(it).delete() } }
    }

    suspend fun getEpisode(id: String): EpisodeEntity? = dao.getEpisode(id)
    suspend fun getPodcast(id: String): PodcastEntity? = dao.getPodcast(id)
    fun podcastEpisodes(podcastId: String): Flow<List<EpisodeEntity>> = dao.observeEpisodes(podcastId)
    suspend fun refresh(podcastId: String): PodcastEntity = refreshFeed(podcastId)
    suspend fun setPlayed(id: String, played: Boolean) = dao.setPlayed(id, played)
    suspend fun savePosition(id: String, position: Long) = dao.savePosition(id, position.coerceAtLeast(0))

    // --- Download: tutte le scritture sono condizionate al token (vedi PodcastDao) -------------
    suspend fun markQueued(id: String, token: String, expectedState: String, expectedToken: String?) =
        dao.markQueued(id, token, expectedState, expectedToken)
    suspend fun updateProgressIfOwner(id: String, token: String, bytes: Long, total: Long?) = dao.updateProgressIfOwner(id, token, bytes, total)
    suspend fun requeueIfOwner(id: String, token: String, error: String? = null) = dao.requeueIfOwner(id, token, error)
    suspend fun markFailedIfOwner(id: String, token: String, error: String? = null) = dao.markFailedIfOwner(id, token, error)
    suspend fun cancelPending(id: String) = dao.cancelPending(id)
    suspend fun pendingDownloads() = dao.getPendingDownloads()
    suspend fun pendingDownloads(podcastId: String) = dao.getPendingDownloads(podcastId)
    suspend fun fileOwners() = dao.getFileOwners()

    /**
     * Controllo del token, rinomina del file e passaggio a DOWNLOADED in un'unica transazione:
     * la transazione prende subito il lock di scrittura, quindi un annullamento o un nuovo
     * accodamento avviene o prima (e qui si rinuncia) o dopo (e trova il download già concluso).
     * Restituisce false se il download non appartiene più a questo worker.
     */
    suspend fun finalizeDownload(id: String, token: String, part: File, target: File, bytes: Long): Boolean =
        database.withTransaction {
            val row = dao.getEpisode(id)
            if (row == null || row.downloadToken != token || row.downloadState !in PENDING_STATES) return@withTransaction false
            if (!part.renameTo(target)) throw IOException("Impossibile salvare il file scaricato.")
            if (dao.markDownloadedIfOwner(id, token, target.absolutePath, bytes) != 1) {
                target.delete()
                return@withTransaction false
            }
            true
        }

    fun isVerifiedLocal(episode: EpisodeEntity): Boolean {
        return OfflineFileVerifier.verifiedFile(episodesDirectory, episode) != null
    }

    suspend fun removeEpisode(id: String) = withContext(Dispatchers.IO) {
        val path = database.withTransaction {
            val path = dao.getEpisode(id)?.localPath
            dao.deleteEpisode(id)
            path
        }
        path?.let { runCatching { File(it).delete() } }
    }

    private fun canonicalUrl(value: String): String {
        val trimmed = value.trim()
        val uri = Uri.parse(trimmed)
        require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()) {
            "Inserisci un indirizzo web HTTP o HTTPS valido."
        }
        return uri.buildUpon().fragment(null).build().toString()
    }

    companion object {
        val PENDING_STATES = setOf("QUEUED", "DOWNLOADING")
    }
}
