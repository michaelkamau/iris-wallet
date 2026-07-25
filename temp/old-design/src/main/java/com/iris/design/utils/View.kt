package com.iris.design.utils

import android.content.Context
import android.util.DisplayMetrics
import kotlin.math.roundToInt

@Deprecated("Old design system. Use `:iris-design` and Material3")
fun Float.dpToPx(context: Context): Float {
    return this * (context.resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)
}

@Deprecated("Old design system. Use `:iris-design` and Material3")
fun Int.dpToPx(context: Context): Int {
    return this.toFloat().dpToPx(context).roundToInt()
}
