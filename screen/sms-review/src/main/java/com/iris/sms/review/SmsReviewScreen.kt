package com.iris.sms.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iris.navigation.screenScopedViewModel

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
                navigationIcon = {
                    IconButton(onClick = { onEvent(SmsReviewEvent.OnClose) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
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
                onEvent = onEvent,
            )
        }
    }
}

@Composable
private fun CapturedItemCard(
    item: CapturedItemUi,
    expanded: Boolean,
    onEvent: (SmsReviewEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
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
            if (expanded && item.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = item.description, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(8.dp))
            StatusLine(item = item)

            Spacer(modifier = Modifier.height(12.dp))
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
