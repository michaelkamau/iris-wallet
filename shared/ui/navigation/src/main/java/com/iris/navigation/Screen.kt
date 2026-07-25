package com.iris.navigation

/**
 * Marks a screen in the Iris Wallet's navigation graph.
 * Extend it when creating a new screen.
 */
sealed interface Screen {
    /**
     * Marks whether a given screen is a legacy Iris Wallet one.
     * If it's a legacy screen, it automatically adds a Surface to make it work.
     * Do NOT mark new Material3 screens as legacy.
     */
    val isLegacy: Boolean
        get() = false
}
