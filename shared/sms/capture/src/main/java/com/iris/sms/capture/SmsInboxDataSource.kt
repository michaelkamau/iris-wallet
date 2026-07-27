package com.iris.sms.capture

import android.content.Context
import android.provider.Telephony
import com.iris.data.model.sms.SenderId
import com.iris.domain.usecase.sms.SmsInboxPage
import com.iris.domain.usecase.sms.SmsInboxReader
import com.iris.sms.parser.RawSmsMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** The one hard boundary applied to every historical inbox query (FR-030). */
object HistoricalImportWindow {
    const val DAYS = 30L
}

/**
 * Android's bounded, paged view of the SMS inbox.
 *
 * The date predicate is deliberately part of the provider selection rather than a Kotlin filter:
 * messages older than the import window never enter the process, including their bodies.
 */
@Singleton
class SmsInboxDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SmsInboxReader {

    override suspend fun readWindow(
        from: Instant,
        to: Instant,
        page: Int,
    ): SmsInboxPage = withContext(Dispatchers.IO) {
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            PROJECTION,
            SELECTION,
            arrayOf(from.toEpochMilli().toString(), to.toEpochMilli().toString()),
            "${Telephony.Sms.DATE} ASC, ${Telephony.Sms._ID} ASC " +
                "LIMIT $PAGE_SIZE OFFSET ${page * PAGE_SIZE}",
        )?.use { cursor ->
            val addressColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

            val hasMore = cursor.count == PAGE_SIZE
            val messages = buildList {
                while (cursor.moveToNext()) {
                    val sender = cursor.getString(addressColumn)
                        ?.let(SenderId::from)
                        ?.getOrNull()
                        ?: continue
                    add(
                        RawSmsMessage(
                            sender = sender,
                            body = cursor.getString(bodyColumn).orEmpty(),
                            receivedAt = Instant.ofEpochMilli(cursor.getLong(dateColumn)),
                        ),
                    )
                }
            }
            SmsInboxPage(messages = messages, hasMore = hasMore)
        } ?: SmsInboxPage(messages = emptyList(), hasMore = false)
    }

    private companion object {
        const val PAGE_SIZE = 200
        const val SELECTION = "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} <= ?"
        val PROJECTION = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
    }
}
