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
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.utilities.Utils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notifies the user when the device gets a network connection back while BrAPI fields still have
 * observations that are not on the server.
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
        const val NOTIFICATION_ID = 7310

        // Flaky connections drop and return often, one reminder covers them all.
        private val RENOTIFY_INTERVAL_MS = TimeUnit.MINUTES.toMillis(10)
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
     * Posts the reminder if any BrAPI field has unsynced observations. Called by
     * [BrapiReconnectWorker] once the device is connected again.
     */
    @SuppressLint("MissingPermission") // checked by canNotify()
    suspend fun notifyIfUnsynced(force: Boolean = false): Unit = withContext(Dispatchers.IO) {

        if (!isEnabled() || !canNotify()) return@withContext


        val unsynced = try {
            autoSyncManager.eligibleFields()
                .map { field -> field to engine.countUnsynced(field.studyId) }
                .filter { (_, count) -> count > 0 }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to count unsynced observations", e)
            return@withContext
        }

        if (unsynced.isEmpty()) return@withContext

        val total = unsynced.sumOf { it.second }

        // The reminder limit only holds back a repeat of the same count. Data collected since the
        // last reminder is always reported, and force, a sync the user asked for from the
        // notification, is always answered.
        val now = System.currentTimeMillis()
        val lastNotified = preferences.getLong(PreferenceKeys.BRAPI_SYNC_LAST_RECONNECT_NOTIFY, 0L)
        val lastTotal = preferences.getInt(PreferenceKeys.BRAPI_SYNC_LAST_RECONNECT_TOTAL, -1)
        if (!force && total == lastTotal && now - lastNotified < RENOTIFY_INTERVAL_MS) return@withContext

        // One field goes straight to its sync screen, several go to the field list to pick from.
        // Either way the back stack leads through the app (field details, field list, main
        // screen) rather than out of it.
        val stack = TaskStackBuilder.create(context)
            .addNextIntent(Intent(context, ConfigActivity::class.java))

        val text = if (unsynced.size == 1) {
            val field = unsynced.first().first
            stack.addNextIntent(
                Intent(context, FieldEditorActivity::class.java)
                    .putExtra(FieldEditorActivity.EXTRA_FIELD_DETAIL_ID, field.studyId)
            )
            stack.addNextIntent(
                Intent(context, BrapiSyncActivity::class.java)
                    .putIntegerArrayListExtra(BrapiSyncActivity.FIELD_IDS, arrayListOf(field.studyId))
            )
            context.getString(R.string.brapi_sync_notification_text_field, field.alias)
        } else {
            stack.addNextIntent(Intent(context, FieldEditorActivity::class.java))
            context.resources.getQuantityString(
                R.plurals.brapi_sync_notification_text_fields,
                unsynced.size,
                unsynced.size
            )
        }

        val pendingIntent = stack.getPendingIntent(
            0,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_drawer_collect_data)
            .setContentTitle(
                context.resources.getQuantityString(
                    R.plurals.brapi_sync_notification_title,
                    total,
                    total
                )
            )
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_field_sync,
                context.getString(R.string.brapi_sync_notification_action_sync),
                actionIntent(BrapiNotificationActionReceiver.ACTION_SYNC, unsynced.map { it.first.studyId })
            )
            .addAction(
                R.drawable.close,
                context.getString(R.string.brapi_sync_notification_action_dismiss),
                actionIntent(BrapiNotificationActionReceiver.ACTION_DISMISS)
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            preferences.edit {
                putLong(PreferenceKeys.BRAPI_SYNC_LAST_RECONNECT_NOTIFY, now)
                putInt(PreferenceKeys.BRAPI_SYNC_LAST_RECONNECT_TOTAL, total)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission was revoked", e)
        }
    }

    private fun actionIntent(action: String, studyIds: List<Int> = emptyList()): PendingIntent {

        val intent = Intent(context, BrapiNotificationActionReceiver::class.java)
            .setAction(action)
            .putExtra(BrapiNotificationActionReceiver.EXTRA_STUDY_IDS, studyIds.toIntArray())

        // A distinct request code per action, otherwise FLAG_UPDATE_CURRENT would let the second
        // button's intent replace the first.
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
