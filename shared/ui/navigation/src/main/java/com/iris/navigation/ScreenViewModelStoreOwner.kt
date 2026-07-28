package com.iris.navigation

import androidx.compose.runtime.Stable
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras

/**
 * A [ViewModelStoreOwner] that lives and dies with one screen.
 *
 * Screen scoping used to be done by clearing the Activity's store when a screen was left. That
 * cleared it *after* the next screen had already asked for its ViewModel during composition, so
 * the screen being entered was handed an instance that was destroyed a moment later — and the
 * replacement, created on the following recomposition, never re-ran the `LaunchedEffect` that
 * loads it. A store per screen removes the shared state that made that possible: leaving a
 * screen can only ever clear that screen's own ViewModels.
 *
 * The factory and creation extras are the [parent]'s, so Hilt keeps constructing
 * `@HiltViewModel` classes exactly as it does for the Activity.
 */
@Stable
class ScreenViewModelStoreOwner(
    parent: ViewModelStoreOwner,
) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {

    private val parentDefaults = requireNotNull(parent as? HasDefaultViewModelProviderFactory) {
        "The parent ViewModelStoreOwner must provide a default factory"
    }

    override val viewModelStore: ViewModelStore = ViewModelStore()

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = parentDefaults.defaultViewModelProviderFactory

    override val defaultViewModelCreationExtras: CreationExtras
        get() = parentDefaults.defaultViewModelCreationExtras
}
