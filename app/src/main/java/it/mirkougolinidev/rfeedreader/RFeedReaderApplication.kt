package it.mirkougolinidev.rfeedreader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import it.mirkougolinidev.rfeedreader.core.common.DefaultFeeds
import it.mirkougolinidev.rfeedreader.core.common.UserPreferences
import coil.ImageLoader
import coil.ImageLoaderFactory
import it.mirkougolinidev.rfeedreader.core.database.RFeedDatabase
import it.mirkougolinidev.rfeedreader.core.download.DownloadScheduler
import it.mirkougolinidev.rfeedreader.core.network.FeedParser
import it.mirkougolinidev.rfeedreader.core.network.LegacyTrust
import okhttp3.OkHttpClient
import it.mirkougolinidev.rfeedreader.data.repository.PodcastRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RFeedReaderApplication : Application(), ImageLoaderFactory {
    val repository: PodcastRepository by lazy {
        PodcastRepository(this, RFeedDatabase.get(this), FeedParser())
    }

    val downloadScheduler: DownloadScheduler by lazy { DownloadScheduler(this, repository) }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(DOWNLOAD_CHANNEL, "Download podcast", NotificationManager.IMPORTANCE_LOW)
            )
        }
        // Va installato prima del seeding: i feed partono subito dopo.
        LegacyTrust.install(this)
        // Chiude i download rimasti a metà da un processo precedente (kill, crash, aggiornamento).
        applicationScope.launch { runCatching { downloadScheduler.reconcile() } }
        seedDefaultFeeds()
    }

    /**
     * Aggiunge i feed proposti che non sono mai stati inseriti. Gli indirizzi riusciti vengono
     * memorizzati, così un podcast rimosso dall'utente non torna e uno fallito (rete assente)
     * viene ritentato al prossimo avvio.
     */
    private fun seedDefaultFeeds() {
        val preferences = UserPreferences.get(this)
        val seeded = preferences.getStringSet(UserPreferences.SEEDED_FEEDS, emptySet()).orEmpty()
        val pending = DefaultFeeds.URLS.filterNot { it in seeded }
        if (pending.isEmpty()) return
        applicationScope.launch {
            val added = pending.filter { url -> runCatching { repository.addFeedIfMissing(url) }.isSuccess }
            if (added.isNotEmpty()) {
                preferences.edit().putStringSet(UserPreferences.SEEDED_FEEDS, seeded + added).apply()
            }
        }
    }

    /** Coil non passa da HttpsURLConnection: il trust combinato va dato al suo OkHttpClient. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient {
            val builder = OkHttpClient.Builder()
            runCatching {
                val trustManager = LegacyTrust.trustManager(this)
                builder.sslSocketFactory(LegacyTrust.sslContext(this).socketFactory, trustManager)
            }
            builder.build()
        }
        .build()

    companion object {
        const val DOWNLOAD_CHANNEL = "podcast_downloads"
    }
}
