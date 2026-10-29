package com.fieldbook.tracker.activities.brapi.io.sync

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.ConfigActivity
import com.fieldbook.tracker.activities.FieldEditorActivity
import com.fieldbook.tracker.objects.FieldObject
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.utilities.Utils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notifies the user when the device gets a network connection back while BrAPI fields still have
 * observations that are not on the server, with a notification for each field.
 *
 * Losing the network enqueues a one time [BrapiReconnectWorker] that waits for a connection. The
 * work is persisted by WorkManager, so the check still happens if the process is killed while the
 * device is offline.
 */
@Singleton
class BrapiReconnectNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: SharedPreferences,
    private val autoSyncManager: BrapiAutoSyncManager,
    private val engine: BrapiSyncEngine
) : SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val TAG = "BrapiReconnectNotifier"
        const val CHANNEL_ID = "brapi_sync"
        const val RECONNECT_WORK_NAME = "brapi_reconnect_check"

        // One notification per field, posted under this tag with the study id as its id
        private const val NOTIFICATION_TAG = "brapi_sync_field"

        // Flaky connections drop and return often, one reminder covers them all.
        private val RENOTIFY_INTERVAL_MS = TimeUnit.MINUTES.toMillis(10)

        /**
         * Clears a field's reminder, once it is dismissed or its sync screen is opened.
         */
        fun cancel(context: Context, studyId: Int) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_TAG, studyId)
        }
    }

    private val connectivityManager by lazy {
        context.getSystemService(ConnectivityManager::class.java)
    }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun start() {
        createChannel()
        preferences.registerOnSharedPreferenceChangeListener(this)
        updateRegistration()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == PreferenceKeys.BRAPI_ENABLED || key == PreferenceKeys.BRAPI_SYNC_RECONNECT_NOTIFICATION) {
            updateRegistration()
        }
    }

    private fun isEnabled(): Boolean = preferences.getBoolean(PreferenceKeys.BRAPI_ENABLED, false)
            && preferences.getBoolean(PreferenceKeys.BRAPI_SYNC_RECONNECT_NOTIFICATION, false)

    private fun updateRegistration() {

        if (isEnabled()) {

            if (networkCallback != null) return

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) {
                    enqueueReconnectCheck()
                }
            }

            try {
                connectivityManager.registerDefaultNetworkCallback(callback)
                networkCallback = callback
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register network callback", e)
            }

            // Already offline, there will be no loss to observe, so wait for the reconnect now.
            if (!Utils.isConnected(context)) enqueueReconnectCheck()

        } else {

            networkCallback?.let {
                try {
                    connectivityManager.unregisterNetworkCallback(it)
                } catch (e: Exception) {
                    Log.w(TAG, "Network callback was not registered", e)
                }
            }
            networkCallback = null

            WorkManager.getInstance(context).cancelUniqueWork(RECONNECT_WORK_NAME)

            cancelAll()
        }
    }

    // Reminders already posted would otherwise stay until each one is dismissed.
    private fun cancelAll() {
        try {
            context.getSystemService(NotificationManager::class.java)
                ?.activeNotifications
                ?.filter { it.tag == NOTIFICATION_TAG }
                ?.forEach { cancel(context, it.id) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear sync reminders", e)
        }
    }

    private fun enqueueReconnectCheck() {

        val request = OneTimeWorkRequestBuilder<BrapiReconnectWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            // the worker retries when it starts before the device is really back online
            .setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            RECONNECT_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun createChannel() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.brapi_sync_notification_channel),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.brapi_sync_notification_channel_description)
        }

        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /**
     * Whether the device has a working internet connection right now, asked directly rather than
     * through WorkManager. Its connectivity tracker can lag behind a network loss, which starts the
     * reconnect check while the device is still going offline.
     */
    fun isOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun canNotify(): Boolean {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return false

        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Posts a reminder for each BrAPI field with unsynced observations, and clears the reminder of
     * any field that has since been synced. Called by [BrapiReconnectWorker] once the device is
     * connected again.
     */
    @SuppressLint("MissingPermission") // checked by canNotify()
    suspend fun notifyIfUnsynced(): Unit = withContext(Dispatchers.IO) {

        if (!isEnabled() || !canNotify()) return@withContext

        // Only what the server accepts, a reminder about data it never will is no use. Every field
        // belongs to the same server, so it is asked once.
        val counts = try {
            val support = engine.uploadSupport()
            autoSyncManager.eligibleFields().map { field ->
                field to engine.countUnsynced(field.studyId, support).sendable
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to count unsynced observations", e)
            return@withContext
        }

        val notificationManager = NotificationManagerCompat.from(context)

        counts.filter { (_, count) -> count == 0 }.forEach { (field, _) ->
            notificationManager.cancel(NOTIFICATION_TAG, field.studyId)
        }

        val unsynced = counts.filter { (_, count) -> count > 0 }
        if (unsynced.isEmpty()) return@withContext

        // The reminder limit only holds back a field repeating the same count, data collected
        // since the last reminder is always reported.
        val now = System.currentTimeMillis()
        val lastNotified = preferences.getLong(PreferenceKeys.BRAPI_SYNC_LAST_RECONNECT_NOTIFY, 0L)
        val lastCounts = lastNotifiedCounts()
        val recent = now - lastNotified < RENOTIFY_INTERVAL_MS

        val toNotify = unsynced.filter { (field, count) -> !recent || lastCounts[field.studyId] != count }
        if (toNotify.isEmpty()) return@withContext

        try {

            toNotify.forEach { (field, count) ->
                notificationManager.notify(NOTIFICATION_TAG, field.studyId, buildNotification(field, count))
            }

            preferences.edit {
                putLong(PreferenceKeys.BRAPI_SYNC_LAST_RECONNECT_NOTIFY, now)
                putString(
                    PreferenceKeys.BRAPI_SYNC_LAST_RECONNECT_COUNTS,
                    unsynced.joinToString(",") { (field, count) -> "${field.studyId}:$count" }
                )
            }

        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission was revoked", e)
        }
    }

    // Each field's unsynced count when reminders were last posted, keyed by study id.
    private fun lastNotifiedCounts(): Map<Int, Int> =
        preferences.getString(PreferenceKeys.BRAPI_SYNC_LAST_RECONNECT_COUNTS, null)
            .orEmpty()
            .split(",")
            .mapNotNull { entry ->
                val (studyId, count) = entry.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
                studyId.toIntOrNull()?.let { id -> count.toIntOrNull()?.let { id to it } }
            }
            .toMap()

    private fun buildNotification(field: FieldObject, count: Int) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_drawer_collect_data)
            .setContentTitle(
                context.resources.getQuantityString(R.plurals.brapi_sync_notification_title, count, count)
            )
            .setContentText(context.getString(R.string.brapi_sync_notification_text_field, field.alias))
            // tapping the notification and its Sync button both open this field's sync screen
            .setContentIntent(syncScreenIntent(field.studyId))
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_field_sync,
                context.getString(R.string.brapi_sync_notification_action_sync),
                syncScreenIntent(field.studyId)
            )
            .addAction(
                R.drawable.close,
                context.getString(R.string.brapi_sync_notification_action_dismiss),
                dismissIntent(field.studyId)
            )
            .build()

    /**
     * Opens a field's sync screen, with its field details, the field list and the main screen
     * behind it, so going back leads through the app rather than out of it.
     */
    private fun syncScreenIntent(studyId: Int): PendingIntent? = TaskStackBuilder.create(context)
        .addNextIntent(Intent(context, ConfigActivity::class.java))
        .addNextIntent(
            Intent(context, FieldEditorActivity::class.java)
                .putExtra(FieldEditorActivity.EXTRA_FIELD_DETAIL_ID, studyId)
        )
        .addNextIntent(
            Intent(context, BrapiSyncActivity::class.java)
                .putIntegerArrayListExtra(BrapiSyncActivity.FIELD_IDS, arrayListOf(studyId))
        )
        // the study id as request code, so each field's notification keeps its own intent
        .getPendingIntent(studyId, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun dismissIntent(studyId: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        studyId,
        Intent(context, BrapiNotificationActionReceiver::class.java)
            .setAction(BrapiNotificationActionReceiver.ACTION_DISMISS)
            .putExtra(BrapiNotificationActionReceiver.EXTRA_STUDY_ID, studyId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
