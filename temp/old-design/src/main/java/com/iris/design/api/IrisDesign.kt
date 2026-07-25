package com.iris.design.api

import com.iris.base.legacy.Theme
import com.iris.design.IrisContext
import com.iris.design.l0_system.IrisColors
import com.iris.design.l0_system.IrisShapes
import com.iris.design.l0_system.IrisTypography

@Deprecated("Old design system. Use `:iris-design` and Material3")
interface IrisDesign {
    @Deprecated("Old design system. Use `:iris-design` and Material3")
    fun context(): IrisContext

    @Deprecated("Old design system. Use `:iris-design` and Material3")
    fun typography(): IrisTypography

    @Deprecated("Old design system. Use `:iris-design` and Material3")
    fun colors(theme: Theme, isDarkModeEnabled: Boolean): IrisColors

    @Deprecated("Old design system. Use `:iris-design` and Material3")
    fun shapes(): IrisShapes
}
