package com.iris.wallet.android.notification

import android.content.Context
import androidx.core.app.NotificationCompat

class IrisNotification(
    context: Context,
    val irisChannel: IrisNotificationChannel
) : NotificationCompat.Builder(context, irisChannel.channelId)
