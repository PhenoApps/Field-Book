package com.fieldbook.tracker.utilities

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.fieldbook.tracker.R
import com.fieldbook.tracker.preferences.GeneralKeys
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.qualifiers.ActivityContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.phenoapps.security.SecureBluetoothActivityImpl
import org.phenoapps.utils.BaseDocumentTreeUtil
import java.io.FileInputStream
import javax.inject.Inject

/**
 * This util is currently setup to share files to nearby devices
 * The files are saved at receiver's device within 'shared' folder eg. root/preferences/shared
 *
 * Stop advertising/discovering as soon as transfer succeeds/fails and terminate all connections
 */
class NearbyShareUtil @Inject constructor(@ActivityContext private val context: Context) {

    interface FileHandler {
        fun getSaveFileDirectory(): Int // specifies parent directory for file to be stored in
        fun onFileReceived(receivedFile: DocumentFile)
        fun prepareFileForTransfer(): DocumentFile?
    }

    private data class Endpoint(val id: String, val name: String)

    private var fileHandler: FileHandler? = null

    private var connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)
    private var docFile: DocumentFile? = null

    private var isDiscovering = false
    private var isAdvertising = false

    private val mEstablishedConnections = mutableSetOf<String>()
    private val mPendingConnections = mutableMapOf<String, Endpoint>()
    private val mDiscoveredEndpoints = mutableMapOf<String, Endpoint>()

    private var deviceSelectionDialog: AlertDialog? = null
    private var authenticationDialog: AlertDialog? = null
    private var progressDialog: AlertDialog? = null
    private var progressBar: ProgressBar? = null
    private var progressStatusIcon: ImageView? = null
    private var progressMessage: TextView? = null

    // track the status of the last initiated transfer
    private var isMostRecentTransferSuccess: Boolean = false

    private val scope = CoroutineScope(Dispatchers.Main)

    @Inject
    lateinit var prefs: SharedPreferences

    companion object {
        private const val TAG = "NearbyShareUtil"
        private val STRATEGY = Strategy.P2P_STAR
        private const val SERVICE_ID = "com.fieldbook.tracker.SERVICE_ID"
    }

    private val secureBluetooth by lazy {
        SecureBluetoothActivityImpl(context as FragmentActivity)
    }

    /**
     * Call this function in the fragment/activity
     */
    fun initialize() {
        if (context is LifecycleOwner) {
            val currentState = (context as LifecycleOwner).lifecycle.currentState
            if (currentState.isAtLeast(Lifecycle.State.STARTED)) {
                Log.e(TAG, "Initialize called too late in lifecycle")
            } else { // state = INITIALIZED or CREATED are okay
                secureBluetooth.initialize()
            }
        } else {
            secureBluetooth.initialize()
        }
    }

    /**
     * Executed by the receiving device
     * One copy is saved by default by the API in device's downloads
     * Manually save one copy in specified directory's "shared" folder
     */
    private fun handleReceivedFile(payloadFile: Payload.File?) {
        scope.launch {
            withContext(Dispatchers.IO) {
                payloadFile?.asParcelFileDescriptor()?.let { pfd ->
                    try {
                        val fileName = payloadFile.asUri()?.lastPathSegment ?: "unknown"
                        val sharedDir = fileHandler?.getSaveFileDirectory()?.let { dirId ->
                            BaseDocumentTreeUtil.createDir(
                                context,
                                getString(dirId),
                                getString(R.string.dir_shared)
                            )
                        }
                        val docFile = sharedDir?.createFile("*/*", fileName)

                        docFile?.let { file ->
                            context.contentResolver?.openOutputStream(file.uri)
                                ?.use { outputStream ->
                                    FileInputStream(pfd.fileDescriptor).use { inputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                }
                        }

                        try {
                            if (docFile != null) {
                                fileHandler?.onFileReceived(docFile)
                            }
                            withContext(Dispatchers.Main) {
                                Utils.makeToast(context, getString(R.string.nearby_share_file_imported_successfully, fileName))
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Log.e("PreferencesFragment", "Failed to import file", e)
                                setProgressStatus(getString(R.string.nearby_share_failed_import, e.message), R.drawable.ic_transfer_error)
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            setProgressStatus(getString(R.string.nearby_share_failed_handle_file, e.message), R.drawable.ic_transfer_error)
                            FirebaseCrashlytics.getInstance().recordException(e)
                        }
                    }
                }
            }
        }
    }

    /**
     * Executed by the sender device
     * Create and send the payload for the file to be transferred
     */
    private fun sendFile(endpointId: String) {
        scope.launch {
            val payload = docFile?.let { documentFile ->
                try {
                    context.contentResolver?.openFileDescriptor(documentFile.uri, "r")?.let { pfd ->
                        Payload.fromFile(pfd).apply {
                            documentFile.name?.let { name ->
                                try {
                                    setFileName(name)
                                    setProgressMessage(getString(R.string.nearby_share_starting_transfer, name))
                                } catch (e: Exception) {
                                    Log.e("NearbyShareUtil", "Failed to set filename", e)
                                    setProgressStatus(getString(R.string.nearby_share_failed_payload), R.drawable.ic_transfer_error)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    setProgressStatus(getString(R.string.nearby_share_failed_payload), R.drawable.ic_transfer_error)
                    null
                }
            }

            payload?.let {
                connectionsClient.sendPayload(endpointId, it)
                    .addOnSuccessListener {
                        setProgressMessage(getString(R.string.nearby_share_transfer_in_progress))
                    }
                    .addOnCompleteListener {
                        setProgressMessage(getString(R.string.nearby_share_transfer_complete))
                    }
                    .addOnFailureListener { e ->
                        setProgressStatus(getString(R.string.nearby_share_failed_transfer, e.message), R.drawable.ic_transfer_error)
                        stopAdvertising()
                    }
            } ?: run {
                setProgressStatus(getString(R.string.nearby_share_failed_payload), R.drawable.ic_transfer_error)
                stopAdvertising()
            }
        }
    }

    fun startSharing(fileHandler: FileHandler) {
        this.fileHandler = fileHandler
        startAdvertising()
    }

    fun startReceiving(fileHandler: FileHandler) {
        this.fileHandler = fileHandler
        startDiscovering()
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.FILE -> {
                    if (isDiscovering) {
                        setProgressMessage(getString(R.string.nearby_share_receiving_file))
                        handleReceivedFile(payload.asFile())
                    }
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            when (update.status) {
                PayloadTransferUpdate.Status.SUCCESS -> {
                    isMostRecentTransferSuccess = true
                    setProgressStatus(getString(R.string.nearby_share_transfer_complete), R.drawable.ic_transfer_complete)
                    stopNearbyShare()
                }
                PayloadTransferUpdate.Status.IN_PROGRESS -> {
                    setProgressMessage(getString(R.string.nearby_share_transfer_in_progress))
                }
                PayloadTransferUpdate.Status.FAILURE -> {
                    setProgressStatus(getString(R.string.nearby_share_transfer_failed), R.drawable.ic_transfer_error)
                    stopNearbyShare()
                }
                PayloadTransferUpdate.Status.CANCELED -> {
                    setProgressStatus(getString(R.string.nearby_share_transfer_cancelled), R.drawable.ic_transfer_cancelled)
                    stopNearbyShare()
                }
            }
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId == SERVICE_ID) {
                mDiscoveredEndpoints[endpointId] = Endpoint(endpointId, info.endpointName)
                showDeviceSelectionDialog()
            }
        }

        override fun onEndpointLost(endpointId: String) {
            mDiscoveredEndpoints.remove(endpointId)

            // dismiss the dialog if the all discovered endpoints were lost
            if (mDiscoveredEndpoints.isEmpty()) {
                Utils.makeToast(context, getString(R.string.nearby_share_no_devices_found))
                deviceSelectionDialog?.dismiss()
            }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        // add endpoint to pending connections
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            mPendingConnections[endpointId] = Endpoint(endpointId, info.endpointName)
            setProgressMessage(getString(R.string.nearby_share_authenticating_connection, info.endpointName))

            showAuthDialog(context, endpointId, info)
        }

        // remove endpoint from pending connections, add to established connections if successfully connected
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            mPendingConnections.remove(endpointId)

            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    setProgressMessage(getString(R.string.nearby_share_connection_established))
                    mEstablishedConnections.add(endpointId)
                    docFile?.let { _ ->
                        sendFile(endpointId)
                    }
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    mEstablishedConnections.remove(endpointId)
                    authenticationDialog?.dismiss()
                    setProgressStatus(getString(R.string.nearby_share_connection_rejected), R.drawable.ic_transfer_cancelled)
                    stopNearbyShare()
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    mEstablishedConnections.remove(endpointId)
                    authenticationDialog?.dismiss()
                    setProgressStatus(getString(R.string.nearby_share_connection_error), R.drawable.ic_transfer_error)
                    stopNearbyShare()
                }
            }
        }

        // remove from established connections
        override fun onDisconnected(endpointId: String) {
            if (!isMostRecentTransferSuccess) {
                setProgressStatus(getString(R.string.nearby_share_devices_disconnected), R.drawable.ic_transfer_error)
            }
            isMostRecentTransferSuccess = false
            mEstablishedConnections.remove(endpointId)
            stopNearbyShare()
        }
    }

    private fun startAdvertising() {
        isMostRecentTransferSuccess = false

        if (isAdvertising) {
            Utils.makeToast(context, getString(R.string.nearby_share_already_waiting_for_receivers))
            return
        }

        secureBluetooth.withNearby { _ ->
            setProgressMessage(getString(R.string.nearby_share_generating_file))

            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        docFile = fileHandler?.prepareFileForTransfer()
                    }
                } catch (e: Exception) {
                    setProgressStatus(getString(R.string.nearby_share_failed_export_generation_message, e.message), R.drawable.ic_transfer_error)
                    stopNearbyShare()
                    FirebaseCrashlytics.getInstance().recordException(e)
                    return@launch
                }

                if (docFile == null) {
                    setProgressStatus(getString(R.string.nearby_share_failed_export_generation), R.drawable.ic_transfer_error)
                    stopNearbyShare()
                    return@launch
                }

                val advertisingOptions = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()

                try {
                    connectionsClient.startAdvertising(getDeviceName(), SERVICE_ID, connectionLifecycleCallback, advertisingOptions)
                        .addOnSuccessListener {
                            isAdvertising = true
                            setProgressMessage(getString(R.string.nearby_share_waiting_for_receivers))
                        }.addOnFailureListener { e ->
                            stopNearbyShare()
                            setProgressStatus(getString(R.string.nearby_share_failed_advertising, e.message), R.drawable.ic_transfer_error)
                        }
                        .addOnCanceledListener {
                            stopNearbyShare()
                            setProgressStatus(getString(R.string.nearby_share_connection_cancelled), R.drawable.ic_transfer_cancelled)
                        }
                } catch (e: Exception) {
                    stopNearbyShare()
                    setProgressStatus(getString(R.string.nearby_share_failed_advertising, e.message), R.drawable.ic_transfer_error)
                    FirebaseCrashlytics.getInstance().recordException(e)
                }
            }
        }
    }

    private fun startDiscovering() {
        isMostRecentTransferSuccess = false

        if (isDiscovering) {
            Utils.makeToast(context, getString(R.string.nearby_share_already_searching_for_sender))
            return
        }

        secureBluetooth.withNearby { _ ->
            // for certain android versions (observed for android 16)
            // even though BLUETOOTH_SCAN is requested in discoverWith
            // the request result isn't registered immediately
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    // if permission is missing, request it again
                    secureBluetooth.withPermission(arrayOf(Manifest.permission.BLUETOOTH_SCAN)) {
                        startDiscovering()  // retry after permission is granted
                    }
                    return@withNearby
                }
            }

            val discoveryOptions = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()

            try {
                connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, discoveryOptions)
                    .addOnSuccessListener {
                        isDiscovering = true
                        setProgressMessage(getString(R.string.nearby_share_searching_for_senders))
                    }.addOnFailureListener { e ->
                        stopNearbyShare()
                        setProgressStatus(getString(R.string.nearby_share_failed_discovery, e.message), R.drawable.ic_transfer_error)
                    }
                    .addOnCanceledListener {
                        stopNearbyShare()
                        setProgressStatus(getString(R.string.nearby_share_connection_cancelled), R.drawable.ic_transfer_cancelled)
                    }
            } catch (e: Exception) {
                stopNearbyShare()
                setProgressStatus(getString(R.string.nearby_share_failed_discovery, e.message), R.drawable.ic_transfer_error)
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }

    private fun disconnectEstablishedConnections() {
        try {
            mEstablishedConnections.toList().forEach { endpointId ->
                try {
                    connectionsClient.disconnectFromEndpoint(endpointId)
                } catch (e: Exception) {
                    Log.e("NearbyShareUtil", "Error disconnecting from endpoint $endpointId: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e("NearbyShareUtil", "Error during disconnection: ${e.message}", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    fun cleanup() {
        stopNearbyShare()
        scope.cancel()
    }

    /**
     * Function to stop advertising/discovering
     */
    private fun stopNearbyShare() {
        disconnectEstablishedConnections()

        if (isAdvertising) stopAdvertising()
        if (isDiscovering) stopDiscovering()

        resetParameters()
    }

    private fun stopAdvertising() {
        if (isAdvertising) {
            connectionsClient.stopAdvertising()
            isAdvertising = false
        }
    }

    private fun stopDiscovering() {
        if (isDiscovering) {
            connectionsClient.stopDiscovery()
            isDiscovering = false
        }
    }

    private fun resetParameters() {
        mEstablishedConnections.clear()
        mPendingConnections.clear()
        mDiscoveredEndpoints.clear()
        deviceSelectionDialog?.dismiss()
        authenticationDialog?.dismiss()
    }

    private fun showProgressDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_nearby_share, null)

        progressBar = dialogView.findViewById(R.id.progress_bar)
        progressStatusIcon = dialogView.findViewById(R.id.progress_status_icon)
        progressMessage = dialogView.findViewById(R.id.progress_message)

        progressDialog?.dismiss()
        progressDialog = AlertDialog.Builder(context, R.style.AppAlertDialog)
            .setView(dialogView)
            .setNegativeButton(R.string.dialog_cancel) { d, _ ->
                stopNearbyShare()
                d.dismiss()
                progressDialog = null
            }
            .setCancelable(true)
            .setOnCancelListener {
                // stop sharing when tapped outside the dialog window
                stopNearbyShare()
                progressDialog?.dismiss()
                progressDialog = null
            }
            .create()

        progressDialog?.show()
    }

    /**
     * Changes the dialog message, retains the progressBar
     */
    private fun setProgressMessage(message: String) {
        if (progressDialog == null) showProgressDialog()
        progressBar?.visibility = View.VISIBLE
        progressStatusIcon?.visibility = View.GONE
        progressMessage?.text = message
    }

    /**
     * Changes the dialog message, and shows icon instead of progressBar
     */
    private fun setProgressStatus(message: String, icon: Int) {
        if (progressDialog == null) showProgressDialog()

        progressDialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.text = context.getString(R.string.dialog_close)

        progressBar?.visibility = View.GONE
        progressStatusIcon?.apply {
            visibility = View.VISIBLE
            setImageResource(icon)
        }
        progressMessage?.text = message
    }

    private fun showDeviceSelectionDialog() {
        if (mDiscoveredEndpoints.isEmpty()) {
            Utils.makeToast(context, getString(R.string.nearby_share_no_devices_found))
            return
        }

        val endpoints = mDiscoveredEndpoints.values.toList()
        val deviceNames = endpoints.map { it.name }.toTypedArray()

        deviceSelectionDialog?.dismiss() // dismiss any existing dialog

        deviceSelectionDialog = AlertDialog.Builder(context, R.style.AppAlertDialog)
            .setTitle(getString(R.string.dialog_device_selection_title))
            .setItems(deviceNames) { dialog, which ->
                val selectedEndpoint = endpoints[which]
                connectionsClient.requestConnection(getDeviceName(), selectedEndpoint.id, connectionLifecycleCallback)
                    .addOnFailureListener { e ->
                        Utils.makeToast(context, getString(R.string.nearby_share_failed_request, e.message))
                    }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.dialog_cancel)) { dialog, _ ->
                dialog.dismiss()
                stopNearbyShare()
                setProgressStatus(getString(R.string.nearby_share_connection_cancelled), R.drawable.ic_transfer_cancelled)
            }
            .setCancelable(true)
            .setOnCancelListener {
                // stop the nearby share when tapped outside of the dialog
                stopNearbyShare()
                setProgressStatus(getString(R.string.nearby_share_connection_cancelled), R.drawable.ic_transfer_cancelled)
            }
            .show()
    }

    private fun showAuthDialog(context: Context, endpointId: String, info: ConnectionInfo) {
        authenticationDialog = AlertDialog.Builder(context, R.style.AppAlertDialog)
            .setTitle(getString(R.string.dialog_nearby_authentication_title, info.endpointName))
            .setMessage(getString(R.string.dialog_nearby_authentication_summary, info.authenticationDigits))
            .setPositiveButton(getString(R.string.dialog_accept)) { _, _ ->
                connectionsClient.acceptConnection(endpointId, payloadCallback)
            }
            .setNegativeButton(R.string.dialog_cancel){ _, _ ->
                connectionsClient.rejectConnection(endpointId)
            }
            .show()
    }

    private fun getString(stringRes: Int): String {
        return context.getString(stringRes)
    }

    private fun getString(stringRes: Int, vararg formatArgs: Any?): String {
        return context.getString(stringRes, *formatArgs)
    }

    // if user has defined custom device name, add the device build model to the name
    private fun getDeviceName(): String {
        val deviceName = prefs.getString(GeneralKeys.DEVICE_NAME, Build.MODEL) ?: Build.MODEL

        return if (deviceName != Build.MODEL) {
            "$deviceName (${Build.MODEL})"
        } else {
            deviceName
        }
    }
}