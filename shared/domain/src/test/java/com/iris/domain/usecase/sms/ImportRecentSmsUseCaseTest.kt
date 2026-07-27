package com.iris.domain.usecase.sms

import arrow.core.left
import arrow.core.right
import com.iris.data.model.sms.CapturedEntry
import com.iris.data.model.sms.MessageFingerprint
import com.iris.sms.parser.RawSmsMessage
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class ImportRecentSmsUseCaseTest {

    private val captureSms = mockk<CaptureSmsUseCase>()
    private val useCase = ImportRecentSmsUseCase(captureSms)

    @Test
    fun `the import asks for exactly the previous 30 days and never captures older rows`() = runTest {
        // given a reader that enforces the provider-side date selection
        val now = Instant.parse("2026-07-27T10:25:34Z")
        val inWindow = SmsFixtures.message().copy(receivedAt = now.minusSeconds(60))
        val older = SmsFixtures.message(body = "older").copy(receivedAt = now.minusSeconds(31 * DAY))
        val inbox = BoundedInbox(messages = listOf(older, inWindow))
        capturedMessagesReturnSuccessfully()

        // when
        val result = useCase.import(
            inbox = inbox,
            from = now.minusSeconds(30 * DAY),
            to = now,
        )

        // then the lower bound is part of the request, before any body is supplied to capture
        inbox.requests.single().from shouldBe now.minusSeconds(30 * DAY)
        inbox.requests.single().to shouldBe now
        result shouldBe SmsImportProgress(processed = 1, captured = 1)
        coVerify(exactly = 1) { captureSms.capture(inWindow, any()) }
        coVerify(exactly = 0) { captureSms.capture(older, any()) }
    }

    @Test
    fun `rows handled live or dismissed before import are counted but never captured again`() = runTest {
        // given both fingerprints were already recorded by the shared capture funnel
        val live = SmsFixtures.message()
        val dismissed = SmsFixtures.message(body = "dismissed")
        val inbox = FakeInbox(pages = listOf(listOf(live, dismissed)))
        coEvery { captureSms.capture(any(), any()) } returns
            SmsCaptureError.AlreadyProcessed(MessageFingerprint.unsafe("already-handled")).left()

        // when
        val result = useCase.import(
            inbox = inbox,
            from = Instant.parse("2026-06-27T00:00:00Z"),
            to = Instant.parse("2026-07-27T00:00:00Z"),
        )

        // then no duplicate pending item is reported
        result shouldBe SmsImportProgress(processed = 2, captured = 0)
        coVerify(exactly = 2) { captureSms.capture(any(), any()) }
    }

    @Test
    fun `paging covers more than one 200-row page`() = runTest {
        // given
        val firstPage = (1..200).map { SmsFixtures.message(body = "page-one-$it") }
        val secondPage = listOf(SmsFixtures.message(body = "page-two"))
        val inbox = FakeInbox(pages = listOf(firstPage, secondPage))
        capturedMessagesReturnSuccessfully()

        // when
        val progress = mutableListOf<SmsImportProgress>()
        val result = useCase.import(
            inbox = inbox,
            from = Instant.parse("2026-06-27T00:00:00Z"),
            to = Instant.parse("2026-07-27T00:00:00Z"),
            onProgress = progress::add,
        )

        // then every page has reached the one shared capture path
        inbox.requestedPages shouldContainExactly listOf(0, 1)
        progress shouldContainExactly listOf(
            SmsImportProgress(processed = 200, captured = 200),
            SmsImportProgress(processed = 201, captured = 201),
        )
        result shouldBe SmsImportProgress(processed = 201, captured = 201)
    }

    @Test
    fun `a short valid page still continues when the provider page was full`() = runTest {
        // given one malformed sender was discarded from the first 200-row provider page
        val firstPage = (1..199).map { SmsFixtures.message(body = "first-page-$it") }
        val nextPage = listOf(SmsFixtures.message(body = "next-page"))
        val inbox = FakeInbox(
            pages = listOf(firstPage, nextPage),
            pagesWithMoreRows = setOf(0),
        )
        capturedMessagesReturnSuccessfully()

        // when
        val result = useCase.import(
            inbox = inbox,
            from = Instant.parse("2026-06-27T00:00:00Z"),
            to = Instant.parse("2026-07-27T00:00:00Z"),
        )

        // then a discarded provider row does not hide every later SMS
        inbox.requestedPages shouldContainExactly listOf(0, 1)
        result shouldBe SmsImportProgress(processed = 200, captured = 200)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `cancellation after a page leaves all already captured rows intact`() = runTest {
        // given a cancellation at the page boundary
        val inbox = FakeInbox(
            pages = listOf((1..200).map { SmsFixtures.message(body = "first-page-$it") }),
        )
        capturedMessagesReturnSuccessfully()

        // when cancellation lands immediately before the use case yields to the next page
        val import = launch {
            useCase.import(
                inbox = inbox,
                from = Instant.parse("2026-06-27T00:00:00Z"),
                to = Instant.parse("2026-07-27T00:00:00Z"),
                onProgress = {
                    currentCoroutineContext().cancel(CancellationException("cancel between pages"))
                },
            )
        }
        advanceUntilIdle()

        // then all 200 rows had already crossed the capture boundary before cancellation
        import.isCancelled shouldBe true
        coVerify(exactly = 200) { captureSms.capture(any(), any()) }
    }

    private fun capturedMessagesReturnSuccessfully() {
        coEvery { captureSms.capture(any(), any()) } returns
            CapturedEntry(principal = SmsFixtures.captured(), fee = null).right()
    }

    private class FakeInbox(
        private val pages: List<List<RawSmsMessage>>,
        private val pagesWithMoreRows: Set<Int> = (0 until pages.lastIndex).toSet(),
    ) : SmsInboxReader {
        val requestedPages = mutableListOf<Int>()

        override suspend fun readWindow(
            from: Instant,
            to: Instant,
            page: Int,
        ): SmsInboxPage {
            requestedPages += page
            return SmsInboxPage(
                messages = pages.getOrElse(page) { emptyList() },
                hasMore = page in pagesWithMoreRows,
            )
        }
    }

    private class BoundedInbox(
        private val messages: List<RawSmsMessage>,
    ) : SmsInboxReader {
        val requests = mutableListOf<Request>()

        override suspend fun readWindow(
            from: Instant,
            to: Instant,
            page: Int,
        ): SmsInboxPage {
            requests += Request(from, to)
            val inWindow = if (page == 0) {
                messages.filter { it.receivedAt >= from && it.receivedAt <= to }
            } else {
                emptyList()
            }
            return SmsInboxPage(messages = inWindow, hasMore = false)
        }
    }

    private data class Request(val from: Instant, val to: Instant)

    private companion object {
        const val DAY = 86_400L
    }
}
