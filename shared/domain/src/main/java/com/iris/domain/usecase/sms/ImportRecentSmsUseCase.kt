package com.iris.domain.usecase.sms

import com.iris.data.model.sms.CaptureOrigin
import com.iris.sms.parser.RawSmsMessage
import kotlinx.coroutines.yield
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The platform seam for a bounded SMS inbox query.
 *
 * It lives beside the use case rather than in the Android capture module so the paging and capture
 * rules can be tested on the JVM. [com.iris.sms.capture.SmsInboxDataSource] is its Android
 * implementation.
 */
interface SmsInboxReader {
    suspend fun readWindow(
        from: Instant,
        to: Instant,
        page: Int,
    ): SmsInboxPage
}

/**
 * A provider page keeps its continuation bit separate from the usable messages.
 *
 * A malformed originating address cannot construct [RawSmsMessage] and is intentionally discarded,
 * but it must not make a full provider page look short or prevent the next page from being read.
 */
data class SmsInboxPage(
    val messages: List<RawSmsMessage>,
    val hasMore: Boolean,
)

data class SmsImportProgress(
    val processed: Int,
    val captured: Int,
)

/**
 * Reads an already-bounded inbox window through the same capture funnel used by a live broadcast.
 *
 * Every row reaches [CaptureSmsUseCase], including rows it declines because they were captured,
 * dismissed or ignored before. That shared funnel is the dedupe guarantee; this use case never
 * invents a second interpretation of "already processed".
 */
@Singleton
class ImportRecentSmsUseCase @Inject constructor(
    private val captureSms: CaptureSmsUseCase,
) {

    suspend fun import(
        inbox: SmsInboxReader,
        from: Instant,
        to: Instant,
        onProgress: suspend (SmsImportProgress) -> Unit = {},
    ): SmsImportProgress {
        var processed = 0
        var captured = 0
        var page = 0
        var hasNextPage: Boolean

        do {
            val inboxPage = inbox.readWindow(from = from, to = to, page = page)
            hasNextPage = inboxPage.hasMore
            inboxPage.messages.forEach { message ->
                processed++
                captureSms.capture(message, origin = CaptureOrigin.HistoricalImport)
                    .onRight { captured++ }
            }
            if (inboxPage.messages.isNotEmpty()) {
                onProgress(SmsImportProgress(processed = processed, captured = captured))
            }
            if (hasNextPage) {
                page++
                yield()
            }
        } while (hasNextPage)

        return SmsImportProgress(processed = processed, captured = captured)
    }
}
