package ru.anjeib.margin.voice

import android.app.ActivityManager
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Speech on the phone comes by itself: after the first start, over Wi-Fi, with the battery and storage fine,
 * the model is fetched in the background (resuming where it stopped). Nothing to find in the settings —
 * dictation simply keeps working when the network or the cloud doesn't.
 */
class ModelWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (!LocalWhisper.supported) return Result.success()
        if (LocalWhisper.ready(applicationContext)) return Result.success()
        LocalWhisper.download(applicationContext, pick(applicationContext))
        return if (LocalWhisper.state.value is LocalWhisper.State.Ready) Result.success() else Result.retry()
    }

    companion object {
        /** The accurate model (190 MB, good Russian) when the phone has the memory for it; the light one (60 MB) otherwise. */
        fun pick(ctx: Context): LocalWhisper.Model {
            val mi = ActivityManager.MemoryInfo()
            ctx.getSystemService(ActivityManager::class.java)?.getMemoryInfo(mi)
            return if (mi.totalMem >= 3_500_000_000L) LocalWhisper.Model.Small else LocalWhisper.Model.Base
        }

        fun schedule(ctx: Context) {
            if (!LocalWhisper.supported || LocalWhisper.ready(ctx)) return
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                "speech-model", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ModelWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                    .build(),
            )
        }
    }
}
