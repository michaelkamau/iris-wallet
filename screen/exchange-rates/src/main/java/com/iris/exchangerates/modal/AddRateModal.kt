package com.iris.exchangerates.modal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.iris.design.l0_system.Orange
import com.iris.design.l0_system.UI
import com.iris.design.l0_system.style
import com.iris.design.l1_buildingBlocks.SpacerVer
import com.iris.exchangerates.RatesEvent
import com.iris.exchangerates.data.RateUi
import com.iris.legacy.IrisWalletPreview
import com.iris.legacy.utils.selectEndTextFieldValue
import com.iris.wallet.ui.theme.components.IrisNameTextField
import com.iris.wallet.ui.theme.modal.IrisModal
import com.iris.wallet.ui.theme.modal.ModalAdd
import com.iris.wallet.ui.theme.modal.ModalTitle
import com.iris.wallet.ui.theme.modal.edit.AmountModal
import java.util.UUID

@Composable
fun BoxWithConstraintsScope.AddRateModal(
    visible: Boolean,
    baseCurrency: String,
    dismiss: () -> Unit,
    onAdd: (RatesEvent.AddRate) -> Unit,
) {
    var toCurrency by remember { mutableStateOf(selectEndTextFieldValue("")) }
    var amountModalVisible by remember { mutableStateOf(false) }
    var rate by remember { mutableStateOf<Double?>(null) }

    IrisModal(
        id = null,
        visible = visible,
        dismiss = dismiss,
        PrimaryAction = {
            ModalAdd {
                val to = toCurrency
                val finalRate = rate
                onAdd(
                    RatesEvent.AddRate(
                        RateUi(
                            from = baseCurrency,
                            to = to.text,
                            rate = finalRate ?: 0.0,
                        )
                    )
                )
                dismiss()
            }
        }
    ) {
        SpacerVer(height = 16.dp)
        ModalTitle(text = "Add rate")
        SpacerVer(height = 24.dp)

        IrisNameTextField(
            modifier = Modifier.padding(horizontal = 32.dp),
            underlineModifier = Modifier.padding(horizontal = 24.dp),
            value = toCurrency,
            hint = "Currency"
        ) {
            toCurrency = it
        }

        SpacerVer(height = 12.dp)
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    amountModalVisible = true
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            text = "$baseCurrency-${toCurrency.text} = ${rate ?: "???"}",
            style = UI.typo.nH2.style(
                color = Orange,
                textAlign = TextAlign.Center,
            )
        )
        SpacerVer(height = 24.dp)
    }

    AmountModal(
        id = remember { UUID.randomUUID() },
        visible = amountModalVisible,
        currency = "",
        initialAmount = rate,
        decimalCountMax = 12,
        dismiss = { amountModalVisible = false },
        onAmountChanged = {
            rate = it
        }
    )
}

@Preview
@Composable
private fun Preview() {
    IrisWalletPreview {
        AddRateModal(
            visible = true,
            baseCurrency = "USD",
            dismiss = {},
            onAdd = {},
        )
    }
}
