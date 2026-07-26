package com.iris.sms.settings

import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.iris.ui.testing.PaparazziScreenshotTest
import com.iris.ui.testing.PaparazziTheme
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders every state the settings screen can be in, in both themes.
 *
 * The two that matter most are the off state — which is what every existing user gets on upgrade
 * and must look like an ordinary, inert settings page (FR-001, SC-010) — and the rationale, which
 * is the app's entire explanation of what it is about to read (FR-002). If either changes, it
 * should have to be looked at.
 */
@RunWith(TestParameterInjector::class)
class SmsCaptureSettingsScreenshotTest(
    @TestParameter
    private val theme: PaparazziTheme,
) : PaparazziScreenshotTest() {

    @Test
    fun `feature off`() {
        snapshot(theme) {
            SmsCaptureSettingsUi(state = SmsSettingsFixtures.Off, onEvent = {})
        }
    }

    /** The explanation, shown before the system has asked the user anything at all (FR-002). */
    @Test
    fun `the explanation shown before asking`() {
        snapshot(theme) {
            SmsCaptureSettingsUi(
                state = SmsSettingsFixtures.Off.copy(rationaleVisible = true),
                onEvent = {},
            )
        }
    }

    @Test
    fun `feature on with mapped and unmapped senders`() {
        snapshot(theme) {
            SmsCaptureSettingsUi(state = SmsSettingsFixtures.On, onEvent = {})
        }
    }

    @Test
    fun `feature on with nothing waiting`() {
        snapshot(theme) {
            SmsCaptureSettingsUi(
                state = SmsSettingsFixtures.On.copy(pendingCount = 0),
                onEvent = {},
            )
        }
    }

    /** A refusal, with the retry that Acceptance 4.2 requires. */
    @Test
    fun `permission denied`() {
        snapshot(theme) {
            SmsCaptureSettingsUi(
                state = SmsSettingsFixtures.Off.copy(permissionState = SmsPermissionUi.Denied),
                onEvent = {},
            )
        }
    }

    /** A refusal Android will not re-ask, where the only route left is the settings app. */
    @Test
    fun `permission permanently denied`() {
        snapshot(theme) {
            SmsCaptureSettingsUi(
                state = SmsSettingsFixtures.Off.copy(
                    permissionState = SmsPermissionUi.PermanentlyDenied,
                ),
                onEvent = {},
            )
        }
    }
}
