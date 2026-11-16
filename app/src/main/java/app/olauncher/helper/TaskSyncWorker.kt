package app.olauncher.helper

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.olauncher.data.Constants
import app.olauncher.data.Prefs

/** Runs TaskSync for the six-hourly sync and for uploads after launcher edits. */
class TaskSyncWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = when {
        TaskSync.sync(applicationContext) -> Result.success()
        // Retrying cannot fix a missing or rejected token
        Prefs(applicationContext).taskSyncStatus == Constants.TaskSyncStatus.TOKEN -> Result.failure()
        else -> Result.retry()
    }
}
