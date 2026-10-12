package ru.anjeib.margin.voice

import android.content.Context
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
import java.util.concurrent.TimeUnit

/**
 * Speech on the phone comes by itself: after the first start, over Wi-Fi, with the battery and storage fine,
 * the model (GigaAM v3, ~230 MB) is fetched in the background, resuming where it stopped; then once a week the same worker
 * asks Margin's door whether a newer, checked version is out and fetches it (LocalSpeech.ensure). Nothing to find in
 * the settings — dictation simply keeps working when the network or the cloud doesn't.
 */
class ModelWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (!LocalSpeech.supported) return Result.success()
        LocalSpeech.ensure(applicationContext)
        return if (LocalSpeech.state.value is LocalSpeech.State.Ready) Result.success() else Result.retry()
    }

    companion object {
        private fun constraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build()

        fun schedule(ctx: Context) {
            if (!LocalSpeech.supported) return
            val wm = WorkManager.getInstance(ctx)
            // The weekly look for a newer model (and the first download, if the one-off below couldn't finish).
            wm.enqueueUniquePeriodicWork("speech-model-update", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ModelWorker>(7, TimeUnit.DAYS).setConstraints(constraints()).build())
            if (LocalSpeech.ready(ctx)) return
            wm.enqueueUniqueWork("speech-model", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ModelWorker>().setConstraints(constraints())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES).build())
        }
    }
}
