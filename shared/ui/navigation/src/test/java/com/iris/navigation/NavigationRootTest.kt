package com.iris.navigation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.iris.ui.testing.ComposeViewModelTest
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test

/**
 * Screen scoping used to be done by clearing the Activity's whole ViewModelStore when a screen
 * was left. That ran *after* the screen being entered had already taken its ViewModel out of
 * that store, so the new screen rendered an instance that was destroyed a moment later and then
 * silently replaced — and the replacement never re-ran the effect that loads it, which left the
 * SMS review inbox on its spinner for good.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigationRootTest : ComposeViewModelTest() {

    @Test
    fun `a screen keeps one view model across recompositions`() = simulation {
        val navigation = Navigation()
        navigation.navigateTo(SmsCaptureSettingsScreen)

        navigation.render {
            awaitItem()
            navigation.navigateTo(SmsReviewScreen)
            val entered = awaitItem().shouldBeShowing()

            // The state write a screen makes when it finishes loading is what used to hand it a
            // different, never-loaded ViewModel.
            entered.load()

            awaitItem().shouldBeShowing() shouldBe entered
            entered.loaded.shouldBeTrue()
        }
    }

    @Test
    fun `leaving a screen destroys its view models`() = simulation {
        val navigation = Navigation()
        navigation.navigateTo(SmsCaptureSettingsScreen)

        navigation.render {
            val left = awaitItem().shouldBeShowing()
            navigation.navigateTo(SmsReviewScreen)
            val entered = awaitItem().shouldBeShowing()

            left.cleared.shouldBeTrue()
            entered.cleared.shouldBeFalse()
        }
    }

    @Test
    fun `entering a screen leaves the surrounding view models alone`() = simulation {
        val navigation = Navigation()
        val parent = FakeViewModelStoreOwner()
        val appScoped = ViewModelProvider(parent)[TestViewModel::class.java]
        navigation.navigateTo(SmsCaptureSettingsScreen)

        navigation.render(parent) {
            awaitItem()
            navigation.navigateTo(SmsReviewScreen)
            awaitItem()

            appScoped.cleared.shouldBeFalse()
            ViewModelProvider(parent)[TestViewModel::class.java] shouldBe appScoped
        }
    }

    @Test
    fun `legacy screens share the surrounding store`() = simulation {
        val navigation = Navigation()
        val parent = FakeViewModelStoreOwner()
        navigation.navigateTo(SettingsScreen)

        navigation.render(parent) {
            awaitItem().shouldBeShowing() shouldBe ViewModelProvider(parent)[TestViewModel::class.java]
        }
    }

    /**
     * Composes [NavigationRoot] the way `RootActivity` does — a surrounding store owner, and one
     * ViewModel asked for per screen — and emits the ViewModel the screen is currently rendering.
     */
    private suspend fun Navigation.render(
        parent: ViewModelStoreOwner = FakeViewModelStoreOwner(),
        assertions: suspend ReceiveTurbine<TestViewModel?>.() -> Unit,
    ) {
        val navigation = this
        moleculeFlow(RecompositionMode.Immediate) {
            var showing by remember { mutableStateOf<TestViewModel?>(null) }
            CompositionLocalProvider(LocalViewModelStoreOwner provides parent) {
                NavigationRoot(navigation) { screen ->
                    showing = screen?.let { screenScopedViewModel<TestViewModel>() }
                }
            }
            // Read inside the emitting scope, so a screen finishing its load re-emits.
            showing?.loaded
            showing
        }.test {
            assertions()
            cancel()
        }
    }

    private fun TestViewModel?.shouldBeShowing(): TestViewModel =
        requireNotNull(this) { "No screen is being rendered" }

    private fun simulation(body: suspend () -> Unit) {
        try {
            Dispatchers.setMain(Dispatchers.Unconfined)
            runTest { body() }
        } finally {
            Dispatchers.resetMain()
        }
    }

    class TestViewModel : ViewModel() {
        private var loads by mutableIntStateOf(0)

        val loaded: Boolean get() = loads > 0

        var cleared: Boolean = false
            private set

        fun load() {
            loads++
        }

        override fun onCleared() {
            cleared = true
        }
    }

    private class FakeViewModelStoreOwner :
        ViewModelStoreOwner,
        HasDefaultViewModelProviderFactory {
        override val viewModelStore: ViewModelStore = ViewModelStore()
        override val defaultViewModelProviderFactory: ViewModelProvider.Factory =
            ViewModelProvider.NewInstanceFactory()
    }
}
