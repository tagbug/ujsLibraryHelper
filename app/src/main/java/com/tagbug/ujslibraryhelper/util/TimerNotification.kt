package com.tagbug.ujslibraryhelper.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.tagbug.ujslibraryhelper.MainActivity
import com.tagbug.ujslibraryhelper.R

object TimerNotification {
    private const val channelId = "TimerNotificationChannel"
    private const val channelName = "AutoTimer"
    private const val channelDescription = "自动预约的通知"
    private const val channelImportance = NotificationManager.IMPORTANCE_HIGH
    private const val defaultNotificationId = 1

    fun addNotificationChannel(context: Context) {
        // 创建通知渠道
        NotificationChannel(channelId, channelName, channelImportance).apply {
            description = channelDescription
            // 显示通知指示灯
            enableLights(true)
            // 通知时振动
            enableVibration(true)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(this)
        }
    }

    fun createPendingIntent(context: Context, target: Class<*>): PendingIntent {
        val intent = Intent(context, target).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(context, 0, intent, 0)
    }

    fun showSimpleNotification(context: Context, message: String) {
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("江苏大学图书馆预约助手")
            .setContentText(message)
            .setContentIntent(createPendingIntent(context, MainActivity::class.java))
            .build()
            .apply {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(defaultNotificationId, this)
            }
    }

    fun showLargeNotification(context: Context, messageTitle: String, message: String) {
        NotificationCompat.Builder(context, channelId).apply {
            setSmallIcon(R.mipmap.ic_launcher)
            setContentTitle("江苏大学图书馆预约助手")
            setContentText(messageTitle)
            if (message.isNotEmpty()) {
                setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(message)
                )
            }
            setContentIntent(createPendingIntent(context, MainActivity::class.java))
            build().apply {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(defaultNotificationId, this)
            }
        }
    }
}