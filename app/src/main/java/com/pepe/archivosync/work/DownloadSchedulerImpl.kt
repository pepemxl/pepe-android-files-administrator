package com.pepe.archivosync.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.pepe.archivosync.domain.repository.DownloadScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : DownloadScheduler {

    override fun scheduleDownload(downloadId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(
                Data.Builder()
                    .putString(DownloadWorker.KEY_DOWNLOAD_ID, downloadId)
                    .build(),
            )
            .build()

        // One unique chain per record: tapping again while running is a no-op.
        WorkManager.getInstance(context).enqueueUniqueWork(
            DownloadWorker.uniqueName(downloadId),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
