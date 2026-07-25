package com.iris.attributions

import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.iris.ui.testing.PaparazziScreenshotTest
import com.iris.ui.testing.PaparazziTheme
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class AttributionsScreenPaparazziTest(
    @TestParameter
    private val theme: PaparazziTheme,
) : PaparazziScreenshotTest() {
    @Test
    fun `snapshot Attribution Screen`() {
        snapshot(theme) {
            AttributionScreenUiTest(theme == PaparazziTheme.Dark)
        }
    }
}