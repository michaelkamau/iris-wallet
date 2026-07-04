package com.ivy.ui

import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.ivy.design.system.IvyMaterial3Theme
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

open class PaparazziScreenshotTest {

    private val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6_PRO,
        showSystemUi = true,
        maxPercentDifference = 0.001
    )

    // Paparazzi 2.0.0-alpha05's bundled layoutlib spawns a background HandlerThread whose
    // priority-setting call is missing on the JVM, crashing with a NoSuchMethodError that
    // Paparazzi's error collector wraps in a `PaparazziLogger.MultipleFailuresException` and
    // re-throws, even though it's unrelated to the actual rendered snapshot. This is a known,
    // currently-unfixed upstream bug (fixed in layoutlib 16.2.3, not yet shipped in a
    // Paparazzi release): https://github.com/cashapp/paparazzi/issues/2342
    // Remove this workaround once Paparazzi ships with layoutlib >= 16.2.3.
    private val layoutlibThreadNicenessBugWorkaround = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                try {
                    base.evaluate()
                } catch (e: Throwable) {
                    if (!isOnlyKnownLayoutlibThreadNicenessBug(e)) throw e
                }
            }
        }
    }

    @get:Rule
    val rule: RuleChain = RuleChain
        .outerRule(layoutlibThreadNicenessBugWorkaround)
        .around(paparazzi)

    protected fun snapshot(theme: PaparazziTheme, content: @Composable () -> Unit) {
        paparazzi.snapshot {
            IvyMaterial3Theme(
                dark = when (theme) {
                    PaparazziTheme.Light -> false
                    PaparazziTheme.Dark -> true
                },
                isTrueBlack = false
            ) {
                content()
            }
        }
    }
}

enum class PaparazziTheme {
    Light, Dark
}

/**
 * True if [error] is either the known layoutlib `setPosixNicenessInternal` NoSuchMethodError
 * directly, or a `PaparazziLogger.MultipleFailuresException` whose collected causes are *all*
 * that exact error (never swallows a mix that includes a real failure).
 * See https://github.com/cashapp/paparazzi/issues/2342.
 */
private fun isOnlyKnownLayoutlibThreadNicenessBug(error: Throwable): Boolean {
    fun isKnownError(e: Throwable) =
        e is NoSuchMethodError && e.message?.contains("setPosixNicenessInternal") == true

    val isMultipleFailures =
        error.javaClass.name == "app.cash.paparazzi.internal.PaparazziLogger\$MultipleFailuresException"
    return isKnownError(error) || (
        isMultipleFailures &&
            runCatching {
                val causesField = error.javaClass.getDeclaredField("causes")
                causesField.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val causes = causesField.get(error) as List<Throwable>
                causes.isNotEmpty() && causes.all(::isKnownError)
            }.getOrDefault(false)
        )
}