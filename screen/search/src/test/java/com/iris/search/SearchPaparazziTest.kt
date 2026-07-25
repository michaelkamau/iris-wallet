package com.iris.search

import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.iris.ui.testing.PaparazziScreenshotTest
import com.iris.ui.testing.PaparazziTheme
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class SearchPaparazziTest(
    @TestParameter
    private val theme: PaparazziTheme,
) : PaparazziScreenshotTest() {
    @Test
    fun `snapshot Search Screen`() {
        snapshot(theme) {
            SearchUiTest(theme == PaparazziTheme.Dark)
        }
    }
}