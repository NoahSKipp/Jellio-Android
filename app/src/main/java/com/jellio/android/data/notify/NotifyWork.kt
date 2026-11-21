package com.jellio.android.data.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jellio.android.data.catching
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotifyEntryPoint {
    fun poller(): NotificationPoller
}

// The periodic check. A failed check is not retried early: the next period
// tries again, so a server or session problem cannot loop.
class NotifyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val poller = EntryPointAccessors.fromApplication(applicationContext, NotifyEntryPoint::class.java).poller()
        catching { poller.check() }
        return Result.success()
    }
}

object NotifyScheduler {
    private const val UNIQUE_NAME = "jellio_notifications_check"

    // WorkManager keeps the schedule across restarts and reboots.
    fun schedule(context: Context) {
        val request = PeriodicWorkRequest.Builder(NotifyWorker::class.java, 15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }

    fun cancel(context: Context) {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME) }
    }
}
