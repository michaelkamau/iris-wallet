package com.iris.sms.capture

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.iris.data.datastore.DatastoreKeys
import com.iris.domain.usecase.sms.ImportRecentSmsUseCase
import dagger.Binds
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SmsImportWorkState {
    data object Idle : SmsImportWorkState
    data class Running(val processed: Int, val captured: Int) : SmsImportWorkState
    data class Finished(val captured: Int) : SmsImportWorkState
}

interface SmsImportScheduler {
    suspend fun enqueue(now: Instant)

    fun observe(): Flow<SmsImportWorkState>
}

/**
 * The one-time 30-day inbox import.
 *
 * Work input contains only the two window timestamps. Message bodies are read by the worker and
 * passed in memory to the capture use case, never to WorkManager's persisted database.
 */
@HiltWorker
class SmsImportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val importRecentSms: ImportRecentSmsUseCase,
    private val inbox: SmsInboxDataSource,
    private val notifier: SmsCaptureNotifier,
    private val dataStore: DataStore<Preferences>,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val fromMillis = inputData.getLong(INPUT_FROM_EPOCH_MILLIS, Long.MIN_VALUE)
        val toMillis = inputData.getLong(INPUT_TO_EPOCH_MILLIS, Long.MIN_VALUE)
        if (fromMillis == Long.MIN_VALUE || toMillis == Long.MIN_VALUE || fromMillis > toMillis) {
            return Result.failure()
        }

        return try {
            val to = Instant.ofEpochMilli(toMillis)
            val result = importRecentSms.import(
                inbox = inbox,
                from = Instant.ofEpochMilli(fromMillis),
                to = to,
                onProgress = { progress ->
                    setProgress(
                        workDataOf(
                            PROGRESS_PROCESSED to progress.processed,
                            PROGRESS_CAPTURED to progress.captured,
                        ),
                    )
                },
            )
            dataStore.edit {
                it[DatastoreKeys.SMS_HISTORICAL_IMPORT_COMPLETED_AT] = to.toEpochMilli()
            }
            notifier.notifyImportFinished(result.captured)
            Result.success(
                workDataOf(
                    PROGRESS_PROCESSED to result.processed,
                    PROGRESS_CAPTURED to result.captured,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: SecurityException) {
            Timber.w("SMS inbox permission was unavailable during import")
            Result.failure()
        }
    }

    companion object {
        const val UNIQUE_NAME = "sms-historical-import"
        const val PROGRESS_PROCESSED = "processed"
        const val PROGRESS_CAPTURED = "captured"
        const val INPUT_FROM_EPOCH_MILLIS = "from_epoch_millis"
        const val INPUT_TO_EPOCH_MILLIS = "to_epoch_millis"
    }
}

@Singleton
class AndroidSmsImportScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SmsImportScheduler {

    override suspend fun enqueue(now: Instant) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            SmsImportWorker.UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SmsImportWorker>()
                .setInputData(
                    workDataOf(
                        SmsImportWorker.INPUT_FROM_EPOCH_MILLIS to now
                            .minus(HistoricalImportWindow.DAYS, ChronoUnit.DAYS)
                            .toEpochMilli(),
                        SmsImportWorker.INPUT_TO_EPOCH_MILLIS to now.toEpochMilli(),
                    ),
                )
                .build(),
        )
    }

    override fun observe(): Flow<SmsImportWorkState> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(SmsImportWorker.UNIQUE_NAME)
            .map { workInfos -> workInfos.firstOrNull().toImportState() }

    private fun WorkInfo?.toImportState(): SmsImportWorkState = when (this?.state) {
        WorkInfo.State.ENQUEUED,
        WorkInfo.State.BLOCKED,
        WorkInfo.State.RUNNING,
        -> SmsImportWorkState.Running(
            processed = progress.getInt(SmsImportWorker.PROGRESS_PROCESSED, 0),
            captured = progress.getInt(SmsImportWorker.PROGRESS_CAPTURED, 0),
        )

        WorkInfo.State.SUCCEEDED -> SmsImportWorkState.Finished(
            captured = outputData.getInt(SmsImportWorker.PROGRESS_CAPTURED, 0),
        )

        WorkInfo.State.FAILED,
        WorkInfo.State.CANCELLED,
        null,
        -> SmsImportWorkState.Idle
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SmsImportSchedulerModule {
    @Binds
    @Singleton
    abstract fun scheduler(impl: AndroidSmsImportScheduler): SmsImportScheduler
}
