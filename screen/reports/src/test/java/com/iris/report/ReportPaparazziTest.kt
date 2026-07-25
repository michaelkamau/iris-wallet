package com.iris.report

import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.iris.reports.ReportNoFilterUiTest
import com.iris.reports.ReportUiTest
import com.iris.ui.testing.PaparazziScreenshotTest
import com.iris.ui.testing.PaparazziTheme
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class ReportPaparazziTest(
    @TestParameter
    private val theme: PaparazziTheme,
) : PaparazziScreenshotTest() {
    @Test
    fun `snapshot Report Screen`() {
        snapshot(theme) {
            ReportUiTest(theme == PaparazziTheme.Dark)
        }
    }

    @Test
    fun `snapshot Report Screen no filter`() {
        snapshot(theme) {
            ReportNoFilterUiTest(theme == PaparazziTheme.Dark)
        }
    }
}