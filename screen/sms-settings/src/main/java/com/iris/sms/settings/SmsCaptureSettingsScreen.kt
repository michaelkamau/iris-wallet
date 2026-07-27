@file:Suppress("FunctionNaming", "TooManyFunctions")

package com.iris.sms.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.iris.navigation.screenScopedViewModel
import com.iris.ui.component.IrisBackBottomBar

@Composable
fun SmsCaptureSettingsScreenImpl() {
    val viewModel: SmsCaptureSettingsViewModel = screenScopedViewModel()
    val context = LocalContext.current
    val receiveSmsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onEvent(
            SmsCaptureSettingsEvent.OnPermissionResult(
                granted = granted,
                // `shouldShowRequestPermissionRationale` returning false *after* a refusal is
                // Android's only signal for "don't ask again". It is a question only an Activity
                // can answer, so it is settled here rather than in the view model.
                permanentlyDenied = !granted && !shouldShowRationale(context),
            ),
        )
    }
    val readInboxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.onEvent(SmsCaptureSettingsEvent.OnHistoricalImportPermissionResult)
    }

    SmsCaptureSettingsUi(
        state = viewModel.uiState(),
        onEvent = { event ->
            when (event) {
                // The only place the system dialog is ever launched, and it is reachable only
                // from the rationale panel (FR-002).
                SmsCaptureSettingsEvent.OnRationaleAccepted -> {
                    viewModel.onEvent(event)
                    receiveSmsLauncher.launch(Manifest.permission.RECEIVE_SMS)
                }

                SmsCaptureSettingsEvent.OnRequestHistoricalImportPermission -> {
                    readInboxLauncher.launch(Manifest.permission.READ_SMS)
                }

                else -> viewModel.onEvent(event)
            }
        },
        onOpenAppSettings = { context.openAppSettings() },
    )
}

private fun shouldShowRationale(context: android.content.Context): Boolean {
    val activity = context as? Activity ?: return true
    return ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.RECEIVE_SMS,
    )
}

private fun android.content.Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
}

/**
 * Stateless by design, so Paparazzi can render every state without Hilt, a database or a
 * permission checker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsCaptureSettingsUi(
    state: SmsCaptureSettingsState,
    onEvent: (SmsCaptureSettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
    onOpenAppSettings: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "SMS transaction capture") },
            )
        },
        bottomBar = { IrisBackBottomBar(onBack = { onEvent(SmsCaptureSettingsEvent.OnClose) }) },
    ) { innerPadding ->
        Content(
            state = state,
            onEvent = onEvent,
            onOpenAppSettings = onOpenAppSettings,
            contentPadding = innerPadding,
        )
    }

    if (state.rationaleVisible) {
        RationaleDialog(onEvent = onEvent)
    }
}

@Composable
private fun Content(
    state: SmsCaptureSettingsState,
    onEvent: (SmsCaptureSettingsEvent) -> Unit,
    onOpenAppSettings: () -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { MasterSwitch(enabled = state.captureEnabled, onEvent = onEvent) }

        if (!state.captureEnabled) {
            item {
                PermissionNotice(
                    state = state.permissionState,
                    onOpenAppSettings = onOpenAppSettings,
                    onEvent = onEvent,
                )
            }
        }

        if (state.captureEnabled) {
            if (state.pendingCount > 0) {
                item { ReviewAction(count = state.pendingCount, onEvent = onEvent) }
            }
            item { HistoricalImportAction(state = state, onEvent = onEvent) }
            item { SectionHeader(text = "Senders") }
            items(state.senders, key = { it.senderId }) { sender ->
                SenderRow(sender = sender, accounts = state.accounts, onEvent = onEvent)
            }
            item { AddSenderRow(onEvent = onEvent) }
        }
    }
}

@Composable
private fun MasterSwitch(
    enabled: Boolean,
    onEvent: (SmsCaptureSettingsEvent) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Capture transactions from messages",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (enabled) {
                    "New payment messages from the senders below are read on this device."
                } else {
                    "Off. Nothing in your messages is read."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = {
                onEvent(
                    if (it) {
                        SmsCaptureSettingsEvent.OnEnableRequested
                    } else {
                        SmsCaptureSettingsEvent.OnDisable
                    },
                )
            },
        )
    }
}

/**
 * The explanation that has to come *before* the system prompt (FR-002).
 *
 * It answers three questions in the user's own terms: what is read, what is taken out of it, and
 * where it goes. The last line is the one that matters most and is therefore the plainest.
 */
@Composable
private fun RationaleDialog(onEvent: (SmsCaptureSettingsEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(SmsCaptureSettingsEvent.OnRationaleDismissed) },
        title = { Text(text = "Before you turn this on") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Iris reads incoming messages from the payment senders you choose — " +
                        "M-PESA, your bank — and nothing else. Messages from friends, family " +
                        "and every other sender are ignored.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "From those messages it takes the amount, the date, who was paid, " +
                        "the reference and any charge, so you don't have to type them in.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "All of this happens on your phone. No message and nothing taken " +
                        "from one is ever sent anywhere.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "You can turn this off, or switch off any single sender, at any time.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onEvent(SmsCaptureSettingsEvent.OnRationaleAccepted) }) {
                Text(text = "Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(SmsCaptureSettingsEvent.OnRationaleDismissed) }) {
                Text(text = "Not now")
            }
        },
    )
}

/**
 * What the user sees after saying no.
 *
 * A refusal is a decision, not a fault, so this explains the consequence and offers a way back —
 * a second attempt while the system will still ask, the app-settings page once it won't
 * (Acceptance 4.2).
 */
@Composable
private fun PermissionNotice(
    state: SmsPermissionUi,
    onOpenAppSettings: () -> Unit,
    onEvent: (SmsCaptureSettingsEvent) -> Unit,
) {
    when (state) {
        SmsPermissionUi.Granted, SmsPermissionUi.NotRequested -> Unit

        SmsPermissionUi.Denied -> Notice(
            title = "Permission not granted",
            body = "Iris needs permission to see incoming messages before it can capture " +
                "anything. The rest of the app works exactly as before.",
            actionLabel = "Try again",
            onAction = { onEvent(SmsCaptureSettingsEvent.OnEnableRequested) },
        )

        SmsPermissionUi.PermanentlyDenied -> Notice(
            title = "Permission blocked",
            body = "Android will not ask again. You can allow it from the app's permission " +
                "settings if you change your mind. Everything else works as before.",
            actionLabel = "Open app settings",
            onAction = onOpenAppSettings,
        )
    }
}

@Composable
private fun Notice(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onAction) { Text(text = actionLabel) }
        }
    }
}

@Composable
private fun HistoricalImportAction(
    state: SmsCaptureSettingsState,
    onEvent: (SmsCaptureSettingsEvent) -> Unit,
) {
    when (val import = state.importState) {
        HistoricalImportUi.Available -> ImportCard(
            body = "Import payment messages from the last 30 days. Messages stay on this device.",
            actionLabel = "Import recent messages",
            onAction = { onEvent(SmsCaptureSettingsEvent.OnStartHistoricalImport) },
        )

        is HistoricalImportUi.Running -> ImportCard(
            body = "Importing recent messages. ${import.processed} processed so far.",
            actionLabel = null,
            onAction = {},
        )

        is HistoricalImportUi.Finished -> ImportCard(
            body = "${import.captured} ${if (import.captured == 1) "transaction is" else "transactions are"} " +
                "ready for review.",
            actionLabel = null,
            onAction = {},
        )

        HistoricalImportUi.AlreadyRun -> if (state.historicalImportPermissionRequired) {
            ImportCard(
                body = "You can import payment messages from the last 30 days once. " +
                    "Iris reads them only on this device.",
                actionLabel = "Allow inbox access",
                onAction = {
                    onEvent(SmsCaptureSettingsEvent.OnRequestHistoricalImportPermission)
                },
            )
        }
    }
}

@Composable
private fun ImportCard(
    body: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Card(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Import recent payment messages",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
            if (actionLabel != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onAction) { Text(text = actionLabel) }
            }
        }
    }
}

@Composable
private fun ReviewAction(
    count: Int,
    onEvent: (SmsCaptureSettingsEvent) -> Unit,
) {
    Button(
        onClick = { onEvent(SmsCaptureSettingsEvent.OnOpenReview) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Text(text = "Review $count captured ${if (count == 1) "transaction" else "transactions"}")
    }
}

@Composable
private fun SectionHeader(text: String) {
    Column {
        HorizontalDivider()
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/**
 * One sender, entirely on its own terms: its own switch, its own account, its own removal.
 *
 * Nothing here reads or writes another row, which is what makes the independence in
 * Acceptance 4.5 structural rather than a promise.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SenderRow(
    sender: SenderMappingUi,
    accounts: List<AccountPickUi>,
    onEvent: (SmsCaptureSettingsEvent) -> Unit,
) {
    Card(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sender.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        // "Not mapped" is stated rather than left blank: it is the reason the
                        // sender's captures will wait instead of being filed (FR-027a).
                        text = sender.accountName ?: "Not mapped — captures will wait for you",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = sender.enabled,
                    onCheckedChange = {
                        onEvent(
                            SmsCaptureSettingsEvent.OnSenderEnabledChanged(sender.senderId, it),
                        )
                    },
                )
                IconButton(
                    onClick = {
                        onEvent(SmsCaptureSettingsEvent.OnRemoveSender(sender.senderId))
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove ${sender.displayName}",
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                accounts.forEach { account ->
                    FilterChip(
                        selected = account.name == sender.accountName,
                        onClick = {
                            onEvent(
                                SmsCaptureSettingsEvent.OnSenderAccountSelected(
                                    senderId = sender.senderId,
                                    accountId = account.id,
                                ),
                            )
                        },
                        label = { Text(text = account.name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AddSenderRow(onEvent: (SmsCaptureSettingsEvent) -> Unit) {
    var raw by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    Card(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Add payment sender",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Messages from an added sender stay pending until you map an account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = raw,
                onValueChange = { raw = it },
                label = { Text(text = "Sender") },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = name,
                onValueChange = { name = it },
                label = { Text(text = "Display name (optional)") },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = raw.isNotBlank(),
                onClick = {
                    onEvent(SmsCaptureSettingsEvent.OnAddSender(rawSender = raw, displayName = name))
                    raw = ""
                    name = ""
                },
            ) {
                Text(text = "Add sender")
            }
        }
    }
}
