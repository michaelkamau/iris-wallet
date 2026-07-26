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

    /**
     * The enrichment surface (FR-022, FR-023). Everything the user needs to file the payment is
     * on screen at once: the category chips, the account chips and the three correctable fields.
     * If a future change buries any of them behind another tap, this snapshot moves.
     */
    @Test
    fun `item expanded for enrichment`() {
        snapshot(theme) {
            SmsReviewUi(
                state = SmsReviewFixtures.content(
                    SmsReviewFixtures.ReadyToConfirm,
                    expandedItemId = SmsReviewFixtures.ReadyToConfirm.id,
                ),
                onEvent = {},
            )
        }
    }

    /**
     * A payee the user has filed before. The suggestion is visible on the collapsed row *and*
     * pre-selected in the picker, which is the whole of Acceptance 3.2 — it must be obvious
     * enough to override, not hidden until the user opens the editor.
     */
    @Test
    fun `item arriving with a remembered category`() {
        snapshot(theme) {
            SmsReviewUi(
                state = SmsReviewFixtures.content(
                    SmsReviewFixtures.WithSuggestedCategory,
                    expandedItemId = SmsReviewFixtures.WithSuggestedCategory.id,
                ),
                onEvent = {},
            )
        }
    }

    /** The suggestion on a collapsed row, where it has to compete with nothing else for space. */
    @Test
    fun `remembered category on a collapsed row`() {
        snapshot(theme) {
            SmsReviewUi(
                state = SmsReviewFixtures.content(SmsReviewFixtures.WithSuggestedCategory),
                onEvent = {},
            )
        }
    }

    /**
     * The unmapped sender, expanded. The account picker is the only route out of this state and
     * the confirm affordance stays shut until one is chosen (FR-027a).
     */
    @Test
    fun `item needing an account expanded`() {
        snapshot(theme) {
            SmsReviewUi(
                state = SmsReviewFixtures.content(
                    SmsReviewFixtures.NeedsAccount,
                    expandedItemId = SmsReviewFixtures.NeedsAccount.id,
                ),
                onEvent = {},
            )
        }
    }

    /** "Keep it anyway" only exists here, so it only gets proven here (FR-029). */
    @Test
    fun `possible duplicate expanded`() {
        snapshot(theme) {
            SmsReviewUi(
                state = SmsReviewFixtures.content(
                    SmsReviewFixtures.PossibleDuplicate,
                    expandedItemId = SmsReviewFixtures.PossibleDuplicate.id,
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
