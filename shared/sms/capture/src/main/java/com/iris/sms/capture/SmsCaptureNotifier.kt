package com.iris.sms.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells the user something is waiting for review (FR-026).
 *
 * The notification says how many items are pending and nothing else — no amount, no counterparty,
 * no message text — because a lock screen is the one place a captured message is most likely to be
 * read by someone other than its owner (FR-006).
 *
 * A `POST_NOTIFICATIONS` denial degrades to "no notification". It must never degrade to
 * "no capture": the items are already safely in `captured_transactions` by the time this runs.
 */
@Singleton
class SmsCaptureNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun notifyCaptured(pendingCount: Int) {
        if (pendingCount <= 0) return
        post(
            id = CAPTURED_NOTIFICATION_ID,
            title = context.getString(R.string.sms_capture_notification_title),
            text = context.resources.getQuantityString(
                R.plurals.sms_capture_notification_text,
                pendingCount,
                pendingCount,
            ),
        )
    }

    /** Summarises the opt-in inbox import without putting SMS content on the lock screen. */
    fun notifyImportFinished(capturedCount: Int) {
        post(
            id = IMPORT_FINISHED_NOTIFICATION_ID,
            title = context.getString(R.string.sms_import_finished_notification_title),
            text = context.resources.getQuantityString(
                R.plurals.sms_import_finished_notification_text,
                capturedCount,
                capturedCount,
            ),
        )
    }

    private fun post(id: Int, title: String, text: String) {
        val manager = NotificationManagerCompat.from(context)
        ensureChannel()
        if (!manager.areNotificationsEnabled()) {
            Timber.d("Notifications are off; %d captured items wait silently", id)
            return
        }
        // `notify` is annotated as requiring POST_NOTIFICATIONS; a revocation between the check
        // above and the call below would otherwise become a crash inside a broadcast receiver.
        // Caught explicitly rather than via `runCatching` so lint can see the handling too.
        try {
            manager.notify(id, build(title, text))
        } catch (e: SecurityException) {
            Timber.d("Could not post the capture notification: %s", e::class)
        }
    }

    private fun build(title: String, text: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            // Public visibility would put the count on the lock screen; the content stays private.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(reviewIntent())
            .build()

    /**
     * Deliberately an implicit action rather than an explicit `RootActivity` reference: this
     * module sits below `:app` and must not know the activity's class name. The `<intent-filter>`
     * in the app manifest is what binds the two together.
     */
    private fun reviewIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(ACTION_REVIEW)
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Its own channel, created here. `IrisNotificationChannel` lives in the frozen
     * `:temp:legacy-code` module and extending it would drag this feature into it.
     */
    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.sms_capture_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.sms_capture_channel_description)
                setShowBadge(true)
            },
        )
    }

    companion object {
        const val CHANNEL_ID = "sms_capture"
        const val ACTION_REVIEW = "iris.wallet.intent.action.review_captured"

        private const val CAPTURED_NOTIFICATION_ID = 4_101
        private const val IMPORT_FINISHED_NOTIFICATION_ID = 4_102
    }
}
