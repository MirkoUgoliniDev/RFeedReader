package it.mirkougolinidev.rfeedreader.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
    @Query("SELECT * FROM podcasts ORDER BY title COLLATE NOCASE")
    fun observePodcasts(): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM podcasts WHERE id = :id")
    suspend fun getPodcast(id: String): PodcastEntity?

    @Query("SELECT * FROM podcasts WHERE feedUrl = :url LIMIT 1")
    suspend fun findPodcastByUrl(url: String): PodcastEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPodcast(podcast: PodcastEntity)

    @Query("DELETE FROM podcasts WHERE id = :id")
    suspend fun deletePodcast(id: String)

    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY publishedAt DESC")
    fun observeEpisodes(podcastId: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes ORDER BY publishedAt DESC")
    fun observeAllEpisodes(): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE downloadState = 'DOWNLOADED' AND localPath IS NOT NULL ORDER BY publishedAt DESC")
    fun observePlayableEpisodes(): Flow<List<EpisodeEntity>>

    /** Solo gli episodi con un download (in corso, fallito o completato): poche righe invece di migliaia. */
    @Query("SELECT * FROM episodes WHERE downloadState != 'NOT_DOWNLOADED' ORDER BY publishedAt DESC")
    fun observeDownloadEpisodes(): Flow<List<EpisodeEntity>>

    @Query("SELECT podcastId, COUNT(*) AS unplayed FROM episodes WHERE isPlayed = 0 GROUP BY podcastId")
    fun observeUnplayedCounts(): Flow<List<UnplayedCount>>

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun getEpisode(id: String): EpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEpisodes(episodes: List<EpisodeEntity>)

    @Query("UPDATE episodes SET title = :title, description = :description, guid = :guid, enclosureUrl = :enclosureUrl, mimeType = :mimeType, byteLength = :byteLength, publishedAt = :publishedAt, durationMs = :durationMs, imageUrl = :imageUrl WHERE id = :id")
    suspend fun updateEpisodeMetadata(
        id: String, title: String, description: String, guid: String, enclosureUrl: String,
        mimeType: String?, byteLength: Long?, publishedAt: Long?, durationMs: Long?, imageUrl: String?
    )

    @Query("UPDATE episodes SET isPlayed = :played WHERE id = :id")
    suspend fun setPlayed(id: String, played: Boolean)

    @Query("UPDATE episodes SET positionMs = :position WHERE id = :id")
    suspend fun savePosition(id: String, position: Long)

    // --- Stato dei download -------------------------------------------------------------------
    // Ogni scrittura è un singolo UPDATE condizionale, quindi atomico. Le scritture del worker
    // passano solo se il token coincide e il download è ancora in corso: un worker superato
    // (annullato, sostituito, episodio rimosso) aggiorna 0 righe e non tocca niente.

    /**
     * Nuovo accodamento: assegna il token e azzera lo stato precedente, ma solo se la riga è ancora
     * come l'ha letta il chiamante (compare-and-set). Se nel frattempo un worker l'ha portata a
     * DOWNLOADED, aggiorna 0 righe invece di buttare via il file appena scaricato.
     */
    @Query("UPDATE episodes SET downloadState = 'QUEUED', downloadToken = :token, localPath = NULL, downloadedBytes = 0, downloadTotalBytes = NULL, downloadError = NULL WHERE id = :id AND downloadState = :expectedState AND downloadToken IS :expectedToken")
    suspend fun markQueued(id: String, token: String, expectedState: String, expectedToken: String?): Int

    @Query("UPDATE episodes SET downloadState = 'DOWNLOADING', downloadedBytes = :bytes, downloadTotalBytes = :total WHERE id = :id AND downloadToken = :token AND downloadState IN ('QUEUED', 'DOWNLOADING')")
    suspend fun updateProgressIfOwner(id: String, token: String, bytes: Long, total: Long?): Int

    /**
     * Torna in coda con lo stesso token: worker fermato dal sistema (rete persa, vincolo Wi-Fi, kill)
     * o errore temporaneo con nuovo tentativo automatico. WorkManager lo rilancerà.
     */
    @Query("UPDATE episodes SET downloadState = 'QUEUED', downloadedBytes = 0, downloadError = :error WHERE id = :id AND downloadToken = :token AND downloadState IN ('QUEUED', 'DOWNLOADING')")
    suspend fun requeueIfOwner(id: String, token: String, error: String?): Int

    @Query("UPDATE episodes SET downloadState = 'FAILED', downloadToken = NULL, localPath = NULL, downloadedBytes = 0, downloadError = :error WHERE id = :id AND downloadToken = :token AND downloadState IN ('QUEUED', 'DOWNLOADING')")
    suspend fun markFailedIfOwner(id: String, token: String, error: String?): Int

    @Query("UPDATE episodes SET downloadState = 'DOWNLOADED', downloadToken = NULL, localPath = :path, downloadedBytes = :bytes, downloadTotalBytes = :bytes, downloadError = NULL WHERE id = :id AND downloadToken = :token AND downloadState IN ('QUEUED', 'DOWNLOADING')")
    suspend fun markDownloadedIfOwner(id: String, token: String, path: String, bytes: Long): Int

    /** Annullamento da parte dell'utente: toglie il token, quindi ogni scrittura successiva del worker viene ignorata. */
    @Query("UPDATE episodes SET downloadState = 'NOT_DOWNLOADED', downloadToken = NULL, localPath = NULL, downloadedBytes = 0, downloadTotalBytes = NULL, downloadError = NULL WHERE id = :id AND downloadState IN ('QUEUED', 'DOWNLOADING')")
    suspend fun cancelPending(id: String): Int

    @Query("SELECT * FROM episodes WHERE downloadState IN ('QUEUED', 'DOWNLOADING')")
    suspend fun getPendingDownloads(): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId AND downloadState IN ('QUEUED', 'DOWNLOADING')")
    suspend fun getPendingDownloads(podcastId: String): List<EpisodeEntity>

    /**
     * Path e token in un'unica query, quindi su un'unica istantanea: una finalizzazione che passa
     * da (token, nessun path) a (nessun token, path) in mezzo a due query separate faceva sembrare
     * orfano il file appena salvato.
     */
    @Query("SELECT localPath, downloadToken FROM episodes WHERE downloadToken IS NOT NULL OR (downloadState = 'DOWNLOADED' AND localPath IS NOT NULL)")
    suspend fun getFileOwners(): List<FileOwner>

    @Query("UPDATE podcasts SET lastError = :error WHERE id = :id")
    suspend fun setLastError(id: String, error: String?)

    @Query("SELECT COALESCE(SUM(downloadedBytes), 0) FROM episodes WHERE downloadState = 'DOWNLOADED'")
    fun observeDownloadedBytes(): Flow<Long>

    @Query("DELETE FROM episodes WHERE id = :id")
    suspend fun deleteEpisode(id: String)

    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId")
    suspend fun getEpisodes(podcastId: String): List<EpisodeEntity>

    @Query("DELETE FROM episodes WHERE podcastId = :podcastId")
    suspend fun deleteEpisodes(podcastId: String)

    @Transaction
    suspend fun saveFeed(podcast: PodcastEntity, episodes: List<EpisodeEntity>) {
        upsertPodcast(podcast)
        insertEpisodes(episodes)
        episodes.forEach { episode ->
            updateEpisodeMetadata(episode.id, episode.title, episode.description, episode.guid,
                episode.enclosureUrl, episode.mimeType, episode.byteLength, episode.publishedAt,
                episode.durationMs, episode.imageUrl)
        }
    }
}
