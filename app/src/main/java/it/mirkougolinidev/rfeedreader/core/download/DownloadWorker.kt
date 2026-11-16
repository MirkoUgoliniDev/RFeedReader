package it.mirkougolinidev.rfeedreader.core.download

import android.app.Notification
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.StatFs
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import it.mirkougolinidev.rfeedreader.RFeedReaderApplication
import it.mirkougolinidev.rfeedreader.core.database.EpisodeEntity
import it.mirkougolinidev.rfeedreader.data.repository.PodcastRepository
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Scarica un episodio per conto del token ricevuto da [DownloadScheduler].
 *
 * Il worker possiede la riga solo finché il token nel DB coincide: annullamento, nuovo
 * accodamento o rimozione glielo tolgono e da quel momento ogni sua scrittura aggiorna 0 righe.
 * I file portano il token nel nome, quindi due worker dello stesso episodio non scrivono mai
 * sullo stesso file.
 */
class DownloadWorker(context: android.content.Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val repository: PodcastRepository
        get() = (applicationContext as RFeedReaderApplication).repository

    override suspend fun doWork(): Result {
        val episodeId = inputData.getString(KEY_EPISODE_ID) ?: return Result.failure()
        // Lavori accodati da una versione precedente non hanno token: li chiude il riallineamento.
        val token = inputData.getString(KEY_TOKEN) ?: return Result.failure()
        val directory = repository.episodesDirectory
        // Nome diverso per ogni esecuzione: anche due esecuzioni sovrapposte dello stesso lavoro
        // (una fermata ma ancora bloccata in rete, l'altra appena ripartita) non si pestano i piedi.
        val runId = UUID.randomUUID().toString()
        val partFile = File(directory, "${episodeId}_${token}_$runId.part")
        try {
            val episode = repository.getEpisode(episodeId)
            if (episode == null || episode.downloadToken != token || episode.downloadState !in PodcastRepository.PENDING_STATES) {
                return Result.failure() // lavoro superato: non c'è niente da fare
            }
            setForeground(createForegroundInfo(runId, episode.title))
            if (repository.updateProgressIfOwner(episodeId, token, 0, null) == 0) return Result.failure()
            directory.mkdirs()
            val target = File(directory, "${episodeId}_${token}.${safeExtension(episode.enclosureUrl, episode.mimeType)}")
            val downloaded = transfer(episode, token, partFile)
            if (!repository.finalizeDownload(episodeId, token, partFile, target, downloaded)) {
                partFile.delete() // annullato o sostituito mentre finiva: il risultato non serve
            } else {
                // .part di esecuzioni precedenti dello stesso lavoro (processo ucciso a metà):
                // il download è concluso, quindi non servono più a nessuno.
                directory.listFiles()?.filter { it.name.startsWith("${episodeId}_${token}_") && it.name.endsWith(".part") }
                    ?.forEach { it.delete() }
            }
            return Result.success()
        } catch (error: Throwable) {
            // Solo isStopped dice che WorkManager rilancerà il lavoro. Una CancellationException
            // senza stop porterebbe WorkManager a FAILED: la riga non deve restare "in coda".
            val stoppedBySystem = isStopped
            val message = userMessage(error)
            // Errori di rete passeggeri: nuovo tentativo automatico con attesa crescente (vedi
            // setBackoffCriteria in DownloadScheduler) fino a MAX_ATTEMPTS, poi FAILED.
            val retry = stoppedBySystem || (isTransient(error) && runAttemptCount + 1 < MAX_ATTEMPTS)
            withContext(NonCancellable) {
                partFile.delete()
                // In coda con lo stesso token: WorkManager lo rilancerà e la riga non resta con una
                // barra congelata. Se l'utente ha annullato, il token non c'è più ed entrambe le
                // scritture vanno a vuoto.
                if (retry) repository.requeueIfOwner(episodeId, token, if (stoppedBySystem) null else "$message Nuovo tentativo automatico…")
                else repository.markFailedIfOwner(episodeId, token, message)
            }
            if (error is CancellationException || error !is Exception) throw error
            if (retry) return Result.retry()
            return Result.failure(workDataOf("error" to message))
        }
    }

    private suspend fun transfer(episode: EpisodeEntity, token: String, partFile: File): Long = withContext(Dispatchers.IO) {
        // La connessione corrente è condivisa col watchdog: cambia a ogni redirect.
        val connection = AtomicReference<HttpURLConnection?>(null)
        // Una read() bloccata non si accorge della cancellazione della coroutine: questo figlio
        // viene cancellato subito insieme al lavoro e chiude la connessione da un altro thread,
        // così la read() termina con un'eccezione invece di restare appesa.
        // UNDISPATCHED: il try parte subito, quindi il finally scatta anche se la cancellazione
        // arriva prima che il figlio venga schedulato.
        val watchdog = launch(start = CoroutineStart.UNDISPATCHED) { try { awaitCancellation() } finally { connection.get()?.disconnect() } }
        try {
            val opened = openFollowingRedirects(episode.enclosureUrl) { connection.set(it) }
            val status = opened.responseCode
            if (status !in 200..299) throw DownloadException("Errore HTTP $status.", transient = status == 408 || status == 429 || status >= 500)
            val contentType = opened.contentType?.substringBefore(';')?.trim()?.lowercase()
            if (contentType != null && !(contentType.startsWith("audio/") || contentType in setOf("application/ogg", "application/octet-stream", "binary/octet-stream"))) {
                throw DownloadException("Il server non ha restituito un file audio.")
            }
            // Solo il Content-Length HTTP e' autorevole: il length dichiarato nell'enclosure
            // RSS e' spesso un segnaposto (Radio 24 invia 1000 per ogni episodio), quindi serve
            // al piu' come stima per lo spazio su disco e per la barra di avanzamento.
            // getContentLengthLong() e' API 24: leggiamo l'header direttamente per
            // restare compatibili con Android 5.1 senza perdere i file oltre 2 GB.
            val expected = opened.getHeaderField("Content-Length")?.trim()?.toLongOrNull()?.takeIf { it > 0 } ?: -1L
            val total = expected.takeIf { it > 0 } ?: episode.byteLength?.takeIf { it > 0 } ?: -1L
            if (total > 0) {
                val available = StatFs(partFile.parentFile!!.absolutePath).availableBytes
                if (total > available) throw DownloadException("Spazio disponibile insufficiente.")
            }
            var downloaded = 0L
            var lastProgressUpdate = 0L
            if (repository.updateProgressIfOwner(episode.id, token, 0, total.takeIf { it > 0 }) == 0) {
                throw DownloadException("Download annullato.")
            }
            opened.inputStream.use { input ->
                partFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate >= 750) {
                            // 0 righe aggiornate = il download non è più nostro: inutile continuare.
                            if (repository.updateProgressIfOwner(episode.id, token, downloaded, total.takeIf { it > 0 }) == 0) {
                                throw DownloadException("Download annullato.")
                            }
                            setProgress(workDataOf("downloaded" to downloaded, "total" to total))
                            lastProgressUpdate = now
                        }
                    }
                }
            }
            if (downloaded <= 0L) throw DownloadException("Il file ricevuto è vuoto.", transient = true)
            if (expected > 0 && downloaded != expected) throw DownloadException("Il download si è interrotto.", transient = true)
            downloaded
        } finally {
            watchdog.cancel()
            connection.get()?.disconnect()
        }
    }

    /**
     * HttpURLConnection su Android non segue i redirect che cambiano protocollo (http -> https),
     * frequentissimi con i tracker dei podcast (podtrac, op3, chartable...): li seguiamo a mano.
     */
    private suspend fun openFollowingRedirects(start: String, onOpen: (HttpURLConnection) -> Unit): HttpURLConnection {
        var url = URL(start)
        repeat(MAX_REDIRECTS + 1) {
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "RFeedReader/0.1 (Android podcast reader)")
                // Senza gzip trasparente il Content-Length resta e si può verificare la completezza.
                setRequestProperty("Accept-Encoding", "identity")
            }
            onOpen(connection)
            // Se il watchdog è scattato prima di onOpen ha chiuso la connessione precedente:
            // si esce qui e il finally di transfer() chiude quella appena aperta.
            currentCoroutineContext().ensureActive()
            val status = connection.responseCode
            if (status !in 300..399 || status == 304) return connection
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            if (location.isNullOrBlank()) throw DownloadException("Redirect senza destinazione (HTTP $status).")
            url = URL(url, location)
            if (url.protocol !in setOf("http", "https")) throw DownloadException("Redirect verso un indirizzo non valido.")
        }
        throw DownloadException("Troppi redirect.")
    }

    private fun isTransient(error: Throwable): Boolean = when (error) {
        is DownloadException -> error.transient
        is java.io.IOException -> true // timeout, connessione caduta, DNS, TLS interrotto
        else -> false
    }

    private fun userMessage(error: Throwable): String = when (error) {
        is DownloadException -> error.message ?: "Download non riuscito."
        is java.net.SocketTimeoutException -> "Il server non risponde."
        is java.net.UnknownHostException -> "Rete assente o server irraggiungibile."
        is javax.net.ssl.SSLException -> "Connessione sicura non riuscita."
        is java.io.IOException -> "Connessione interrotta."
        else -> "Download non riuscito."
    }

    private fun createForegroundInfo(runId: String, title: String): ForegroundInfo {
        val notification: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(applicationContext, RFeedReaderApplication.DOWNLOAD_CHANNEL)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(applicationContext)
        }.setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Download podcast")
            .setContentText(title)
            .setOngoing(true)
            .build()
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        // Una notifica per esecuzione: con un id condiviso, la fine di un download (o di una vecchia
        // esecuzione dello stesso lavoro) poteva togliere la notifica di quelli ancora in corso.
        return ForegroundInfo(NOTIFICATION_ID_BASE + (runId.hashCode() and 0x7FFFFF), notification, serviceType)
    }

    private fun safeExtension(url: String, mime: String?): String {
        val pathExt = runCatching { URL(url).path }.getOrDefault("").substringAfterLast('.', "").lowercase()
        if (pathExt.matches(Regex("[a-z0-9]{1,5}"))) return pathExt
        return when (mime?.substringBefore(';')?.lowercase()) {
            "audio/mpeg" -> "mp3"
            "audio/mp4", "audio/m4a" -> "m4a"
            "audio/aac" -> "aac"
            "audio/ogg", "application/ogg" -> "ogg"
            "audio/opus" -> "opus"
            "audio/wav", "audio/x-wav" -> "wav"
            else -> "audio"
        }
    }

    companion object {
        const val KEY_EPISODE_ID = "episode_id"
        const val KEY_TOKEN = "download_token"
        private const val NOTIFICATION_ID_BASE = 0x41000
        private const val MAX_ATTEMPTS = 10
        private const val MAX_REDIRECTS = 10
    }

    private class DownloadException(message: String, val transient: Boolean = false) : Exception(message)
}
