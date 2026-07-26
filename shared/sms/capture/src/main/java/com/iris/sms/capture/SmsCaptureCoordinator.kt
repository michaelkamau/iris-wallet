package com.iris.sms.capture

import android.telephony.SmsMessage
import com.iris.base.threading.DispatchersProvider
import com.iris.data.model.sms.SenderId
import com.iris.data.repository.CapturedTransactionRepository
import com.iris.domain.usecase.sms.CaptureSmsUseCase
import com.iris.domain.usecase.sms.SmsCaptureGate
import com.iris.sms.parser.RawSmsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One delivered part of a message, stripped of every Android type.
 *
 * Existing purely so the joining rules below can be tested on the JVM: the alternative is a
 * Robolectric test of logic that has nothing to do with Android.
 */
data class SmsPart(
    val address: String,
    val body: String,
    val timestampMillis: Long,
)

/**
 * Does the work a broadcast receiver is not allowed to do.
 *
 * Owns an application-scoped [CoroutineScope] so capture survives the ten-second receiver budget,
 * and always calls back so the system is told the broadcast is finished — including when
 * everything went wrong (contracts/ui-and-platform-contract.md §4.1).
 *
 * The message body is never logged and never written anywhere but through the capture use case,
 * which stores only a digest of it (FR-006).
 */
@Singleton
class SmsCaptureCoordinator @Inject constructor(
    private val gate: SmsCaptureGate,
    private val captureSms: CaptureSmsUseCase,
    private val capturedTransactionRepository: CapturedTransactionRepository,
    private val notifier: SmsCaptureNotifier,
    dispatchersProvider: DispatchersProvider,
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatchersProvider.io)

    /** Entry point for the receiver: Android types in, nothing but a callback out. */
    fun captureAsync(messages: Array<SmsMessage>?, onFinished: () -> Unit) {
        captureAsync(parts = messages.orEmpty().map(SmsMessage::toPart), onFinished = onFinished)
    }

    fun captureAsync(parts: List<SmsPart>, onFinished: () -> Unit) {
        scope.launch {
            try {
                capture(parts)
            } catch (failure: Throwable) {
                // A receiver that throws is a crash the user did not ask for; the finally below
                // still releases the broadcast.
                Timber.w("SMS capture failed: %s", failure::class.simpleName)
            } finally {
                onFinished()
            }
        }
    }

    private suspend fun capture(parts: List<SmsPart>) {
        // Asked first, so a switched-off user costs one preference read and no parsing at all.
        if (!gate.isCapturing()) {
            Timber.d("SMS capture is off; %d parts discarded", parts.size)
            return
        }

        var captured = 0
        joinSmsParts(parts).forEach { message ->
            captureSms.capture(message)
                .onRight { captured++ }
                .onLeft { Timber.d("Not captured: %s", it::class.simpleName) }
        }
        if (captured > 0) {
            notifier.notifyCaptured(capturedTransactionRepository.pendingCount().first())
        }
    }
}

/**
 * Multipart messages arrive as separate parts of one broadcast, in order, and a long M-PESA
 * confirmation is routinely two of them. Concatenating per originating address is what stops the
 * tail of a message being parsed as if it were a message of its own.
 *
 * The receipt time of the first part wins, because that is when the event happened as far as the
 * user is concerned. A part whose address will not normalise into a [SenderId] — blank, or all
 * whitespace — is dropped rather than guessed at.
 */
internal fun joinSmsParts(parts: List<SmsPart>): List<RawSmsMessage> = parts
    .groupBy { it.address }
    .mapNotNull { (address, group) ->
        SenderId.from(address).getOrNull()?.let { sender ->
            RawSmsMessage(
                sender = sender,
                body = group.joinToString(separator = "") { it.body },
                receivedAt = Instant.ofEpochMilli(group.first().timestampMillis),
            )
        }
    }

private fun SmsMessage.toPart() = SmsPart(
    address = originatingAddress.orEmpty(),
    body = displayMessageBody.orEmpty(),
    timestampMillis = timestampMillis,
)
