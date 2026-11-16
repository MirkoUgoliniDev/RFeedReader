package it.mirkougolinidev.rfeedreader.core.network

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.mirkougolinidev.rfeedreader.RFeedReaderApplication
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class AutomaticRefreshScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun enable() {
        val request = PeriodicWorkRequestBuilder<RefreshFeedsWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun disable() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    companion object { const val WORK_NAME = "periodic-feed-refresh" }
}

class RefreshFeedsWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as RFeedReaderApplication).repository
        val feeds = repository.podcasts.first()
        for (podcast in feeds) {
            try {
                repository.refresh(podcast.id)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The repository records the per-feed error and the next periodic run retries it.
            }
        }
        return Result.success()
    }
}
