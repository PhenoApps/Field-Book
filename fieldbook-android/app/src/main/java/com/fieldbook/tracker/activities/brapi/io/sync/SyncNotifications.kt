package com.fieldbook.tracker.activities.brapi.io.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fieldbook.tracker.R

object SyncNotifications {

    const val CHANNEL_ID = "fieldbook_sync_channel"
    const val SYNC_NOTIFICATION_ID = 1001

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.brapi_sync_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.brapi_sync_channel_desc)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun showSyncComplete(
        context: Context,
        uploaded: Int,
        downloaded: Int,
        conflicts: Int,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val title = context.getString(R.string.brapi_sync_complete_title)
        val body = buildString {
            append(context.getString(R.string.brapi_sync_uploaded, uploaded))
            append(", ")
            append(context.getString(R.string.brapi_sync_downloaded, downloaded))
            if (conflicts > 0) {
                append(" — ")
                append(context.getString(R.string.brapi_sync_conflicts, conflicts))
            }
        }

        val intent = Intent(context, Class.forName("com.fieldbook.tracker.activities.brapi.io.sync.BrapiSyncActivity"))
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_refresh)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(SYNC_NOTIFICATION_ID, notification)
    }
}
