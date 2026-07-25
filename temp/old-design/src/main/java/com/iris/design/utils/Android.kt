package com.iris.design.utils

import android.os.Handler
import android.os.Looper

@Deprecated("Old design system. Use `:iris-design` and Material3")
fun postDelayed(delayMs: Long, run: () -> Unit) {
    Handler(Looper.getMainLooper()).postDelayed({ run() }, delayMs)
}
