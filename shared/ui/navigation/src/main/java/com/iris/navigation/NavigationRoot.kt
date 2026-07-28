package com.iris.navigation

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

@SuppressLint("ComposeCompositionLocalUsage")
private val LocalNavigation = compositionLocalOf<Navigation> { error("No LocalNavigation") }

@Composable
fun NavigationRoot(
    navigation: Navigation,
    navGraph: @Composable (screen: Screen?) -> Unit
) {
    CompositionLocalProvider(
        LocalNavigation provides navigation,
    ) {
        val screen = navigation.currentScreen
        if (screen != null && !screen.isLegacy) {
            ScreenScope(screen) { navGraph(screen) }
        } else {
            // Legacy screens share the Activity's store on purpose: some of them expect to find
            // the same ViewModel instance that another screen created.
            navGraph(screen)
        }
    }
}

/**
 * Runs [content] against a `ViewModelStore` that belongs to [screen] alone.
 *
 * `key` gives each screen its own store, and the store is cleared when — and only when — that
 * screen leaves the composition. Clearing the Activity's store instead, as this used to, ran
 * *after* the screen being entered had already taken its ViewModel out of it, leaving that
 * screen rendering an instance nothing would ever load.
 */
@Composable
private fun ScreenScope(screen: Screen, content: @Composable () -> Unit) {
    val parent = requireNotNull(LocalViewModelStoreOwner.current) {
        "No ViewModelStoreOwner provided"
    }
    key(screen) {
        val owner = remember(parent) { ScreenViewModelStoreOwner(parent) }
        DisposableEffect(owner) {
            onDispose { owner.viewModelStore.clear() }
        }
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            content()
        }
    }
}

@Composable
fun navigation(): Navigation {
    return LocalNavigation.current
}

/**
 * Provides a [ViewModel] instance scoped the screen's life.
 * When the user navigates away from the screen all screen scoped
 * viewModels are destroyed.
 * Does not apply for legacy screens.
 */
@Composable
inline fun <reified T : ViewModel> screenScopedViewModel(
    factory: ViewModelProvider.Factory? = null
): T {
    val viewModelStoreOwner = LocalViewModelStoreOwner.current
    requireNotNull(viewModelStoreOwner) { "No ViewModelStoreOwner provided" }
    val viewModelProvider = factory?.let {
        ViewModelProvider(viewModelStoreOwner, it)
    } ?: ViewModelProvider(viewModelStoreOwner)
    return viewModelProvider[T::class.java]
}
