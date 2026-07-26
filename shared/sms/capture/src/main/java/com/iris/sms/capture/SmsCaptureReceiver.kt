package com.iris.sms.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The only place in the app that hears about a delivered message.
 *
 * It parses nothing, touches no database and holds no state: everything real happens in
 * [SmsCaptureCoordinator], on a scope that outlives the ten seconds a receiver is given.
 * `goAsync` keeps the process alive until the coordinator says it is finished, and the coordinator
 * guarantees it always does — including when capture failed (FR-005).
 */
@AndroidEntryPoint
class SmsCaptureReceiver : BroadcastReceiver() {

    @Inject
    lateinit var coordinator: SmsCaptureCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val pending = goAsync()
        coordinator.captureAsync(
            messages = Telephony.Sms.Intents.getMessagesFromIntent(intent),
            onFinished = pending::finish,
        )
    }
}
