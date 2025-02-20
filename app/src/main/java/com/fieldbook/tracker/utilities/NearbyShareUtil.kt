package com.fieldbook.tracker.utilities

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import com.fieldbook.tracker.R
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
import kotlinx.coroutines.launch
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

    interface FileCallback {
        fun getSaveFileDirectory(): Int // specifies parent directory for file to be stored in
        fun onFileReceived(receivedFile: DocumentFile)
        fun prepareFileForTransfer(): DocumentFile
    }

    interface PermissionCallback {
        fun onPermissionRequest(permissions: Array<String>, requestCode: Int)
    }

    private data class Endpoint(val id: String, val name: String)

    private var fileCallback: FileCallback? = null
    private var permissionCallback: PermissionCallback? = null

    private var connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)
    private var docFile: DocumentFile? = null

    private var isDiscovering = false
    private var isAdvertising = false

    // used while handling permission request
    private var currentState: ShareState? = null

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

    companion object {
        const val REQUEST_PERMISSIONS_CODE = 112
        private val STRATEGY = Strategy.P2P_STAR
        private const val SERVICE_ID = "com.fieldbook.tracker.SERVICE_ID"
    }

    private enum class ShareState {
        ADVERTISING,
        DISCOVERING
    }

    /**
     * Executed by the receiving device
     * One copy is saved by default by the API in device's downloads
     * Manually save one copy in specified directory's "shared" folder
     */
    private fun handleReceivedFile(payloadFile: Payload.File?) {
        payloadFile?.asParcelFileDescriptor()?.let { pfd ->
            try {
                val fileName = payloadFile.asUri()?.lastPathSegment ?: "unknown"
                val sharedDir = fileCallback?.getSaveFileDirectory()?.let { dirId ->
                    BaseDocumentTreeUtil.createDir(context, getString(dirId), getString(R.string.dir_shared))
                }
                val docFile = sharedDir?.createFile("*/*", fileName)

                docFile?.let { file ->
                    context.contentResolver?.openOutputStream(file.uri)?.use { outputStream ->
                        FileInputStream(pfd.fileDescriptor).use { inputStream->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    Utils.makeToast(context, "$fileName imported successfully")
                }

                if (docFile != null) {
                    fileCallback?.onFileReceived(docFile)
                }
            } catch (e: Exception) {
                Utils.makeToast(context, String.format(getString(R.string.nearby_share_failed_handle_file), e.message))
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }

    /**
     * Executed by the sender device
     * Create and send the payload for the file to be transferred
     */
    private fun sendFile(endpointId: String, fileUri: Uri) {
        val payload = docFile?.let { documentFile ->
            try {
                context.contentResolver?.openFileDescriptor(documentFile.uri, "r")?.let { pfd ->
                    Payload.fromFile(pfd).apply {
                        documentFile.name?.let { name ->
                            try {
                                setFileName(name)
                                setProgressMessage(String.format(getString(R.string.nearby_share_starting_transfer), name))
                            } catch (e: Exception) {
                                Log.e("NearbyShareUtil", "Failed to set filename", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
                    setProgressStatus(String.format(getString(R.string.nearby_share_failed_transfer), e.message), R.drawable.ic_transfer_error)
                    stopAdvertising()
                }
        } ?: run {
            setProgressStatus(getString(R.string.nearby_share_failed_payload), R.drawable.ic_transfer_error)
            stopAdvertising()
        }
    }

    fun startSharing(fileCallback: FileCallback, permissionCallback: PermissionCallback) {
        this.fileCallback = fileCallback
        this.permissionCallback = permissionCallback
        startAdvertising()
    }

    fun startReceiving(fileCallback: FileCallback, permissionCallback: PermissionCallback) {
        this.fileCallback = fileCallback
        this.permissionCallback = permissionCallback
        startDiscovering()
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.FILE -> {
                    setProgressMessage(getString(R.string.nearby_share_receiving_file))
                    handleReceivedFile(payload.asFile())
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
            setProgressMessage(String.format(getString(R.string.nearby_share_authenticating_connection), info.endpointName))

            showAuthDialog(context, endpointId, info)
        }

        // remove endpoint from pending connections, add to established connections if successfully connected
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            mPendingConnections.remove(endpointId)

            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    setProgressMessage(getString(R.string.nearby_share_connection_established))
                    mEstablishedConnections.add(endpointId)
                    docFile?.let { file ->
                        val fileUri = file.uri
                        sendFile(endpointId, fileUri)
                        stopNearbyShare()
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

        if (!hasRequiredPermissions()) {
            currentState = ShareState.ADVERTISING
            requestPermissions()
            Utils.makeToast(context, getString(R.string.nearby_share_permissions_required))
            return
        }

        setProgressMessage(getString(R.string.nearby_share_generating_file))

        scope.launch {
            try {
                docFile = fileCallback?.prepareFileForTransfer()
            } catch (e: Exception) {
                stopNearbyShare()
                FirebaseCrashlytics.getInstance().recordException(e)
            }

            val advertisingOptions = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()

            try {
                connectionsClient.startAdvertising(Build.MODEL, SERVICE_ID, connectionLifecycleCallback, advertisingOptions)
                    .addOnSuccessListener {
                        isAdvertising = true
                        setProgressMessage(getString(R.string.nearby_share_waiting_for_receivers))
                    }.addOnFailureListener { e ->
                        stopNearbyShare()
                        setProgressStatus(String.format(getString(R.string.nearby_share_failed_advertising), e.message), R.drawable.ic_transfer_error)
                    }
                    .addOnCanceledListener {
                        stopNearbyShare()
                        setProgressStatus(getString(R.string.nearby_share_connection_cancelled), R.drawable.ic_transfer_cancelled)
                    }
            } catch (e: Exception) {
                stopNearbyShare()
                setProgressStatus(String.format(getString(R.string.nearby_share_failed_advertising), e.message), R.drawable.ic_transfer_error)
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }

    private fun startDiscovering() {
        isMostRecentTransferSuccess = false

        if (isDiscovering) {
            Utils.makeToast(context, getString(R.string.nearby_share_already_searching_for_sender))
            return
        }

        if (!hasRequiredPermissions()) {
            currentState = ShareState.DISCOVERING
            requestPermissions()
            Utils.makeToast(context, getString(R.string.nearby_share_permissions_required))
            return
        }

        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()

        try {
            connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, discoveryOptions)
                .addOnSuccessListener {
                    isDiscovering = true
                    setProgressMessage(getString(R.string.nearby_share_searching_for_senders))
                }.addOnFailureListener { e ->
                    stopNearbyShare()
                    setProgressStatus(String.format(getString(R.string.nearby_share_failed_discovery), e.message), R.drawable.ic_transfer_error)
                }
                .addOnCanceledListener {
                    stopNearbyShare()
                    setProgressStatus(getString(R.string.nearby_share_connection_cancelled), R.drawable.ic_transfer_cancelled)
                }
        } catch (e: Exception) {
            stopNearbyShare()
            setProgressStatus(String.format(getString(R.string.nearby_share_failed_discovery), e.message), R.drawable.ic_transfer_error)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private fun disconnectEstablishedConnections() {
        mEstablishedConnections.toList().forEach { endpointId ->
            connectionsClient.disconnectFromEndpoint(endpointId)
        }
    }

    /**
     * Function to stop advertising/discovering
     */
    fun stopNearbyShare() {
        disconnectEstablishedConnections()

        if (isAdvertising) stopAdvertising()
        if (isDiscovering) stopDiscovering()
    }

    private fun stopAdvertising() {
        if (isAdvertising) {
            connectionsClient.stopAdvertising()
            isAdvertising = false

            resetParameters()
        }
    }

    private fun stopDiscovering() {
        if (isDiscovering) {
            connectionsClient.stopDiscovery()
            isDiscovering = false

            resetParameters()
        }
    }

    private fun resetParameters() {
        mEstablishedConnections.clear()
        mPendingConnections.clear()
        mDiscoveredEndpoints.clear()
        deviceSelectionDialog?.dismiss()
        authenticationDialog?.dismiss()
    }

    fun handlePermissionResult(requestCode: Int, perms: IntArray) {
        if (requestCode == REQUEST_PERMISSIONS_CODE) {
            if (perms.all { it == PackageManager.PERMISSION_GRANTED }) {
                when (currentState) {
                    ShareState.ADVERTISING -> startAdvertising()
                    ShareState.DISCOVERING -> startDiscovering()
                    null -> Log.d("NearbyShareUtil", "No pending operation")
                }
            } else {
                setProgressStatus(getString(R.string.nearby_share_permissions_required), R.drawable.ic_transfer_error)
                Utils.makeToast(context, getString(R.string.nearby_share_permissions_required))
                requestPermissions()
            }
            currentState = null
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                        context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                        context.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                        context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            }
            else -> true
        }
    }

    private fun requestPermissions() {
        val permissions = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                arrayOf(
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }
            else -> {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }
        }

        permissionCallback?.onPermissionRequest(permissions, REQUEST_PERMISSIONS_CODE)
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
                progressDialog = null
                stopNearbyShare()
            }
            .create()

        progressDialog?.show()
    }

    private fun setProgressMessage(message: String) {
        if (progressDialog == null) showProgressDialog()
        progressBar?.visibility = View.VISIBLE
        progressStatusIcon?.visibility = View.GONE
        progressMessage?.text = message
    }

    private fun setProgressStatus(message: String, icon: Int) {
        if (progressDialog == null) showProgressDialog()
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
                connectionsClient.requestConnection(Build.MODEL, selectedEndpoint.id, connectionLifecycleCallback)
                    .addOnFailureListener { e ->
                        Utils.makeToast(context, String.format(getString(R.string.nearby_share_failed_request), e.message))
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
            .setTitle(String.format(getString(R.string.dialog_nearby_authentication_title), info.endpointName))
            .setMessage(String.format(getString(R.string.dialog_nearby_authentication_summary), info.authenticationDigits))
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
}