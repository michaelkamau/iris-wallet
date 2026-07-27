@file:Suppress("FunctionNaming", "TooManyFunctions")

package com.iris.sms.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iris.navigation.screenScopedViewModel
import com.iris.ui.component.IrisBackBottomBar
import kotlinx.collections.immutable.ImmutableList

@Composable
fun SmsReviewScreenImpl() {
    val viewModel: SmsReviewViewModel = screenScopedViewModel()
    SmsReviewUi(state = viewModel.uiState(), onEvent = viewModel::onEvent)
}

/**
 * Stateless by design, so Paparazzi can render every state without Hilt, a database or a clock.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsReviewUi(
    state: SmsReviewState,
    onEvent: (SmsReviewEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Review transactions") },
            )
        },
        bottomBar = { IrisBackBottomBar(onBack = { onEvent(SmsReviewEvent.OnClose) }) },
    ) { innerPadding ->
        when (state) {
            SmsReviewState.Loading -> Loading(Modifier.padding(innerPadding))
            SmsReviewState.Empty -> Empty(Modifier.padding(innerPadding))
            is SmsReviewState.Content -> Content(
                state = state,
                onEvent = onEvent,
                contentPadding = innerPadding,
            )
        }
    }
}

@Composable
private fun Loading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun Empty(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Nothing to review",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Transactions found in your messages will show up here before they " +
                    "reach your accounts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Content(
    state: SmsReviewState.Content,
    onEvent: (SmsReviewEvent) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                text = "${state.pendingCount} waiting — nothing has been added to your " +
                    "accounts yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(items = state.items, key = { it.id }) { item ->
            CapturedItemCard(
                item = item,
                expanded = state.expandedItemId == item.id,
                categories = state.categories,
                accounts = state.accounts,
                onEvent = onEvent,
            )
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun CapturedItemCard(
    item: CapturedItemUi,
    expanded: Boolean,
    categories: ImmutableList<CategoryPickUi>,
    accounts: ImmutableList<AccountPickUi>,
    onEvent: (SmsReviewEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // The whole header is the expand affordance: tapping the row the user is already
            // looking at is the first of the three taps SC-002 allows.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onEvent(SmsReviewEvent.OnItemExpanded(item.id.takeIf { !expanded }))
                    },
            ) {
                Summary(item = item)
            }

            Spacer(modifier = Modifier.height(8.dp))
            StatusLine(item = item)

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                Editor(
                    item = item,
                    categories = categories,
                    accounts = accounts,
                    onEvent = onEvent,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Actions(item = item, onEvent = onEvent)
        }
    }
}

@Composable
private fun Summary(item: CapturedItemUi, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f, fill = false),
                text = item.counterparty.ifBlank { "Unknown payee" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.amountFormatted,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        // The message's own timestamp, never the moment it was captured (FR-014).
        Text(
            text = item.timeFormatted,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        item.reference?.let { reference ->
            Text(
                text = "Ref: $reference",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item.feeFormatted?.let { fee ->
            Text(
                text = "Transaction cost $fee",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item.categoryName?.let { category ->
            Text(
                text = category,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Everything the user can change before the item reaches the ledger.
 *
 * The category picker comes first because it is the one thing every captured item needs and the
 * message can never supply (FR-022). Everything below it is a correction — needed sometimes,
 * never usually — so it sits under the choice rather than in front of it.
 */
@Composable
private fun Editor(
    item: CapturedItemUi,
    categories: ImmutableList<CategoryPickUi>,
    accounts: ImmutableList<AccountPickUi>,
    onEvent: (SmsReviewEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (categories.isNotEmpty()) {
            FieldLabel(text = "Category")
            ChipRow(
                options = categories.map { it.id to it.name },
                selectedLabel = item.categoryName,
                onSelected = { onEvent(SmsReviewEvent.OnCategorySelected(item.id, it)) },
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Offered whether or not the sender is mapped: a message can arrive on the wrong line,
        // and correcting that is FR-023 as much as choosing one is FR-027a.
        if (accounts.isNotEmpty()) {
            FieldLabel(text = "Account")
            ChipRow(
                options = accounts.map { it.id to it.name },
                selectedLabel = item.accountName,
                onSelected = { onEvent(SmsReviewEvent.OnAccountSelected(item.id, it)) },
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = item.description,
            onValueChange = { onEvent(SmsReviewEvent.OnDescriptionChanged(item.id, it)) },
            label = { Text(text = "Description") },
            singleLine = false,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = item.amountEditable,
                onValueChange = { onEvent(SmsReviewEvent.OnAmountEdited(item.id, it)) },
                label = { Text(text = "Amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
            )
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = item.counterparty,
                onValueChange = { onEvent(SmsReviewEvent.OnCounterpartyEdited(item.id, it)) },
                label = { Text(text = "Payee") },
                singleLine = true,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        DateField(item = item, onEvent = onEvent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    item: CapturedItemUi,
    onEvent: (SmsReviewEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerVisible by remember(item.id) { mutableStateOf(false) }

    OutlinedButton(modifier = modifier, onClick = { pickerVisible = true }) {
        Text(text = "Date: ${item.timeFormatted}")
    }

    if (pickerVisible) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = item.timeEpochMillis,
        )
        DatePickerDialog(
            onDismissRequest = { pickerVisible = false },
            confirmButton = {
                Button(
                    onClick = {
                        pickerVisible = false
                        pickerState.selectedDateMillis?.let {
                            onEvent(SmsReviewEvent.OnTimeEdited(item.id, it))
                        }
                    },
                ) {
                    Text(text = "Select")
                }
            },
            dismissButton = {
                TextButton(onClick = { pickerVisible = false }) { Text(text = "Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(
    options: List<Pair<String, String>>,
    selectedLabel: String?,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (id, label) ->
            FilterChip(
                selected = label == selectedLabel,
                onClick = { onSelected(id) },
                label = { Text(text = label) },
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier,
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun Actions(
    item: CapturedItemUi,
    onEvent: (SmsReviewEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                // Confirming without an account is refused, so it must not look available.
                enabled = item.status !is ReviewStatusUi.NeedsAccount,
                onClick = { onEvent(SmsReviewEvent.OnConfirm(item.id)) },
            ) {
                Text(text = "Confirm")
            }
            OutlinedButton(
                onClick = {
                    onEvent(SmsReviewEvent.OnDismiss(item.id, alsoRemoveFee = true))
                },
            ) {
                Text(text = item.feeFormatted?.let { "Dismiss both" } ?: "Dismiss")
            }
        }
        // The offer FR-018 requires, shown only where there is something to offer: a payment
        // with a charge can be thrown away on its own, and the charge stays for review.
        item.feeFormatted?.let {
            TextButton(
                onClick = {
                    onEvent(SmsReviewEvent.OnDismiss(item.id, alsoRemoveFee = false))
                },
            ) {
                Text(text = "Dismiss, keep the cost")
            }
        }
        // Acknowledging the warning is its own action, so the user says "I have looked" rather
        // than having the warning vanish under a confirm they were making anyway (FR-029).
        if (item.status is ReviewStatusUi.PossibleDuplicate) {
            TextButton(onClick = { onEvent(SmsReviewEvent.OnKeepDespiteDuplicate(item.id)) }) {
                Text(text = "Keep it anyway")
            }
        }
    }
}

@Composable
private fun StatusLine(item: CapturedItemUi, modifier: Modifier = Modifier) {
    val (text, color) = when (val status = item.status) {
        ReviewStatusUi.NeedsAccount ->
            "Choose an account before confirming" to MaterialTheme.colorScheme.error

        is ReviewStatusUi.PossibleDuplicate ->
            status.existingSummary to MaterialTheme.colorScheme.tertiary

        ReviewStatusUi.ReadyToConfirm ->
            (item.accountName ?: "Ready to confirm") to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        modifier = modifier,
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
    )
}
