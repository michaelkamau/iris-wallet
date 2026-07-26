package com.iris.sms.review

import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.iris.ui.testing.PaparazziScreenshotTest
import com.iris.ui.testing.PaparazziTheme
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders every state the review inbox can be in, in both themes.
 *
 * The fixtures are static strings, so these snapshots are a function of the code alone. The
 * "needs an account" snapshot exists specifically to keep the disabled confirm affordance
 * honest — it is the visible half of FR-027a.
 */
@RunWith(TestParameterInjector::class)
class SmsReviewScreenshotTest(
    @TestParameter
    private val theme: PaparazziTheme,
) : PaparazziScreenshotTest() {

    @Test
    fun `nothing to review`() {
        snapshot(theme) {
            SmsReviewUi(state = SmsReviewState.Empty, onEvent = {})
        }
    }

    @Test
    fun `single item ready to confirm`() {
        snapshot(theme) {
            SmsReviewUi(
                state = SmsReviewFixtures.content(SmsReviewFixtures.ReadyToConfirm),
                onEvent = {},
            )
        }
    }

    @Test
    fun `item needing an account`() {
        snapshot(theme) {
            SmsReviewUi(
                state = SmsReviewFixtures.content(SmsReviewFixtures.NeedsAccount),
                onEvent = {},
            )
        }
    }

    @Test
    fun `item flagged as a possible duplicate`() {
        snapshot(theme) {
            SmsReviewUi(
                state = SmsReviewFixtures.content(SmsReviewFixtures.PossibleDuplicate),
                onEvent = {},
            )
        }
    }

    @Test
    fun `item with a transaction cost`() {
        snapshot(theme) {
            SmsReviewUi(
                state = SmsReviewFixtures.content(SmsReviewFixtures.WithFee),
                onEvent = {},
            )
        }
    }

    /**
     * The expanded form is where the charge earns its keep: it is the only place the user is
     * offered the choice between discarding both rows and keeping the cost (FR-018).
     */
    @Test
    fun `item with a transaction cost expanded`() {
        snapshot(theme) {
            SmsReviewUi(
                state = SmsReviewFixtures.content(
                    SmsReviewFixtures.WithFee,
                    expandedItemId = SmsReviewFixtures.WithFee.id,
                ),
                onEvent = {},
            )
        }
    }

    @Test
    fun `mixed list`() {
        snapshot(theme) {
            SmsReviewUi(
                state = SmsReviewFixtures.content(
                    SmsReviewFixtures.ReadyToConfirm,
                    SmsReviewFixtures.NeedsAccount,
                    SmsReviewFixtures.WithFee,
                    expandedItemId = SmsReviewFixtures.WithFee.id,
                ),
                onEvent = {},
            )
        }
    }
}
