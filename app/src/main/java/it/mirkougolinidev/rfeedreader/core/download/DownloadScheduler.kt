package it.mirkougolinidev.rfeedreader.core.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import it.mirkougolinidev.rfeedreader.core.common.UserPreferences
import it.mirkougolinidev.rfeedreader.data.repository.PodcastRepository
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Unico punto che modifica insieme Room e WorkManager.
 *
 * - Un solo [lock] di processo serializza accodamento, annullamento, rimozione e riallineamento:
 *   la coppia "scrittura DB + operazione WorkManager" non si intreccia mai con un'altra.
 * - Ogni sezione critica gira in [NonCancellable]: se l'utente cambia schermata a metà,
 *   la coppia viene comunque completata (prima si lasciava una riga QUEUED senza lavoro).
 * - Il worker non prende il lock: è protetto dal token, che rende innocue le sue scritture
 *   quando il download non gli appartiene più.
 */
class DownloadScheduler(private val context: Context, private val repository: PodcastRepository) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    suspend fun enqueue(episodeId: String) = critical { enqueueLocked(episodeId) }

    private suspend fun enqueueLocked(episodeId: String) {
        val episode = repository.getEpisode(episodeId) ?: error("Episodio non trovato.")
        if (repository.isVerifiedLocal(episode)) return
        val currentToken = episode.downloadToken
        if (currentToken != null && episode.downloadState in PodcastRepository.PENDING_STATES && isAlive(episodeId, currentToken)) {
            return // già in coda o in corso: non lo si riavvia da capo
        }
        val token = UUID.randomUUID().toString()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_EPISODE_ID to episodeId, DownloadWorker.KEY_TOKEN to token))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(
                if (UserPreferences.get(context).getBoolean(UserPreferences.WIFI_ONLY, false)) NetworkType.UNMETERED else NetworkType.CONNECTED
            ).build())
            // Nuovi tentativi automatici dopo errori temporanei: 30 s, 1 min, 2 min, 4 min...
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG_DOWNLOAD)
            .addTag(tokenTag(token))
            .build()
        // Prima il token nel DB, poi il lavoro: il worker che parte trova sempre il suo token.
        // Un eventuale worker precedente perde la proprietà in questo istante e REPLACE lo ferma.
        if (repository.markQueued(episodeId, token, episode.downloadState, episode.downloadToken) == 0) {
            return // la riga è cambiata dopo la lettura (es. download appena concluso): non si tocca
        }
        // Il file di prima non ha superato la verifica, quindi non è riproducibile: si libera spazio.
        episode.localPath?.let { runCatching { File(it).delete() } }
        try {
            workManager.enqueueUniqueWork(workName(episodeId), ExistingWorkPolicy.REPLACE, request).await()
        } catch (error: Exception) {
            repository.markFailedIfOwner(episodeId, token, "Impossibile mettere in coda il download.")
            throw error
        }
    }

    suspend fun cancel(episodeId: String) = critical { cancelLocked(episodeId) }

    /** Annulla un eventuale download e rimuove l'episodio, sotto lo stesso lock degli accodamenti. */
    suspend fun removeEpisode(episodeId: String) = critical {
        cancelLocked(episodeId)
        repository.removeEpisode(episodeId)
    }

    /** Annulla i download del podcast e lo rimuove, senza che un accodamento si infili in mezzo. */
    suspend fun removePodcast(podcastId: String) = critical {
        repository.pendingDownloads(podcastId).forEach { cancelLocked(it.id) }
        repository.removePodcast(podcastId)
    }

    /**
     * Da chiamare all'avvio del processo. Rimette in coda le righe in corso che non hanno più un
     * lavoro vivo (processo ucciso, accodamento interrotto, database di una versione precedente) e cancella i file rimasti senza proprietario.
     */
    suspend fun reconcile() = critical {
        val directory = repository.episodesDirectory
        // L'elenco dei file va letto PRIMA del database. Un file esistente a questo punto ha già il
        // suo token nel DB (il token si scrive prima di accodare), quindi non può essere scambiato
        // per orfano; i file creati dopo l'elenco non vengono toccati.
        val files = directory.listFiles()?.toList().orEmpty()

        for (episode in repository.pendingDownloads()) {
            val token = episode.downloadToken
            if (token != null && isAlive(episode.id, token)) continue
            // Nessun lavoro vivo (processo ucciso, accodamento interrotto, versione precedente):
            // invece di lasciarlo bloccato o segnarlo fallito, lo si rimette in coda da capo.
            runCatching { enqueueLocked(episode.id) }
        }

        val owners = repository.fileOwners()
        val keepPaths = owners.mapNotNull { owner -> owner.localPath?.let { runCatching { File(it).canonicalPath }.getOrNull() } }.toSet()
        val keepTokens = owners.mapNotNull { it.downloadToken }.toSet()
        files.forEach { file ->
            val referenced = runCatching { file.canonicalPath }.getOrNull() in keepPaths
            val owned = keepTokens.any { file.name.contains(it) }
            if (!referenced && !owned) runCatching { file.delete() }
        }
    }

    private suspend fun cancelLocked(episodeId: String) {
        // Prima il DB: da qui in poi il worker non è più proprietario e ogni sua scrittura va a vuoto.
        repository.cancelPending(episodeId)
        workManager.cancelUniqueWork(workName(episodeId)).await()
    }

    private suspend fun isAlive(episodeId: String, token: String): Boolean =
        workManager.getWorkInfosForUniqueWork(workName(episodeId)).await()
            .any { !it.state.isFinished && tokenTag(token) in it.tags }

    private suspend fun <T> critical(block: suspend () -> T): T =
        withContext(NonCancellable + Dispatchers.IO) { lock.withLock { block() } }

    companion object {
        const val TAG_DOWNLOAD = "episode-download"
        private val lock = Mutex()
        fun workName(episodeId: String) = "episode-download-$episodeId"
        fun tokenTag(token: String) = "download-token:$token"
    }
}
