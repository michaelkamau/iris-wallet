package com.iris.design.l0_system

import androidx.compose.ui.text.TextStyle

@Deprecated("Old design system. Use `:iris-design` and Material3")
interface IrisTypography {
    val h1: TextStyle
    val h2: TextStyle
    val b1: TextStyle
    val b2: TextStyle
    val c: TextStyle

    val nH1: TextStyle
    val nH2: TextStyle
    val nB1: TextStyle
    val nB2: TextStyle
    val nC: TextStyle
}
