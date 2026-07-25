package com.iris.design.l1_buildingBlocks

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.iris.design.l1_buildingBlocks.data.IrisPadding
import com.iris.design.utils.irisPadding
import com.iris.design.utils.thenIf

@Deprecated("Old design system. Use `:iris-design` and Material3")
@Composable
fun IrisText(
    modifier: Modifier = Modifier,
    text: String,
    typo: TextStyle,
    padding: IrisPadding? = null
) {
    Text(
        modifier = Modifier
            .thenIf(padding != null) {
                irisPadding(irisPadding = padding!!)
            }
            .then(modifier),
        text = text,
        style = typo,
    )
}
