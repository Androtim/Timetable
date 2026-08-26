package com.androtim.timetable.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.androtim.timetable.data.ScheduleRepository
import com.androtim.timetable.widget.TimetableWidgetProvider
import java.util.concurrent.TimeUnit

/** Downloads the iCal feed and refreshes the local cache + widgets. */
class SyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val count = ScheduleRepository.get(applicationContext).sync()
        Log.i(TAG, "Sync OK: $count events cached")
        TimetableWidgetProvider.updateAllWidgets(applicationContext)
        Result.success()
    } catch (e: Throwable) {
        // Throwable, not Exception: an Error (e.g. a class-init failure) must
        // also be logged and retried instead of silently killing the work.
        Log.e(TAG, "Sync failed (attempt $runAttemptCount)", e)
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }

    companion object {
        private const val TAG = "TimetableSync"
        private const val PERIODIC_WORK = "timetable_periodic_sync"
        const val FORCE_WORK = "timetable_force_sync"

        /** Background fetch every 3 hours, only when a network is available. */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(3, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                // ADE servers rate-limit ("Too much requests"); back off
                // gently instead of hammering them.
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** Immediate one-shot sync ("Refresh Now" / widget refresh). */
        fun forceRefresh(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                FORCE_WORK,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
