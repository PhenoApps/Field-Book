package com.fieldbook.tracker.traits

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.adapters.spectral.ColorAdapter
import com.fieldbook.tracker.dialogs.SimpleListDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.nixsensor.universalsdk.CommandStatus
import com.nixsensor.universalsdk.DeviceScanner
import com.nixsensor.universalsdk.DeviceState
import com.nixsensor.universalsdk.IDeviceCompat
import com.nixsensor.universalsdk.IMeasurementData
import com.nixsensor.universalsdk.OnDeviceResultListener
import com.nixsensor.universalsdk.ScanMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.internal.toImmutableList

class SpectralTraitLayout : BaseTraitLayout, ColorAdapter.ColorListListener {

    companion object {
        const val TAG = "SpectralTraitLayout"
        const val type = "spectral"
    }

    private val background = CoroutineScope(Dispatchers.IO)

    private val colorList = mutableListOf<String>()

    private var connectButton: FloatingActionButton? = null
    private var captureButton: FloatingActionButton? = null
    private var disconnectButton: FloatingActionButton? = null
    private var recycler: RecyclerView? = null

    private var isDeleting = false

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    fun setNa() {

        val position = getSelected() ?: -1

        if (position != -1) {
            onColorDeleted(position) {
                insertNa()
            }
        } else {
            insertNa()
        }
    }

    override fun type(): String {
        return "spectral"
    }

    override fun layoutId(): Int {
        return R.layout.traits_spectral
    }

    override fun init(act: Activity) {
        connectButton = act.findViewById(R.id.connect_btn)
        captureButton = act.findViewById(R.id.capture_btn)
        disconnectButton = act.findViewById(R.id.disconnect_btn)
        recycler = act.findViewById(R.id.recycler_view)
        recycler?.adapter = ColorAdapter(this)
    }

    override fun loadLayout() {
        super.loadLayout()

        collectInputView?.visibility = View.GONE

        colorList.clear()

        loadColorList()

        val nixDeviceId = controller.getPreferences().getString("nix_device_id", "") ?: ""
        val nixDeviceName = controller.getPreferences().getString("nix_device_name", "") ?: ""
        val nix = (context as CollectActivity).getNixSensorHelper()

        if (nix.connectedDevice != null) {

            enableCapture(nix.connectedDevice!!)

        } else if (nixDeviceId.isNotBlank() && nixDeviceName.isNotBlank()) {

            val device = nix.getDeviceById(nixDeviceId, nixDeviceName)

            if (device != null) {

                connectDevice(device)

            }

        } else {

            startNixSearch()

            setupConnectButton()

        }
    }

    private fun loadColorList() {

        colorList.clear()

        background.launch {

            val studyId = collectActivity.studyId
            val plot = (context as? CollectActivity)?.observationUnit
            val traitDbId = currentTrait.id
            val observations = database.getAllObservations(studyId, plot, traitDbId)

            withContext(Dispatchers.Main) {
                for (observation in observations) {
                    colorList.add(observation.value)
                }

                submitList()

            }
        }
    }

    private fun startNixSearch() {
        with((context as CollectActivity)) {
            val nix = getNixSensorHelper()
            getSecurityChecker().withNearby {
                nix.search()
            }
        }
    }

    private fun setupConnectButton() {
        connectButton?.setOnClickListener {
            showDeviceListDialog()
        }
    }

    private fun showDeviceListDialog() {
        val nix = (context as CollectActivity).getNixSensorHelper()
        val devices = nix.getDeviceList()

        if (devices.isNotEmpty()) {

            SimpleListDialog.show(
                context,
                R.string.select_device,
                devices,
                devices.map { it.name + " " + it.id }
            ) { device ->
                (device as? IDeviceCompat)?.let { nixDevice ->
                    connectDevice(nixDevice)
                }
            }
        } else {
            Toast.makeText(context, R.string.no_devices_found, Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectDevice(device: IDeviceCompat) {

        val scanner = DeviceScanner(context)
        scanner.stop()

        val nix = (context as CollectActivity).getNixSensorHelper()

        nix.connect(device) { connected ->
            if (connected) {
                saveDevice(device)
                enableCapture(device)
            }
        }
    }

    private fun saveDevice(device: IDeviceCompat) {
        (context as CollectActivity).getNixSensorHelper().saveDevice(device)
        controller.getPreferences().edit()
            .putString("nix_device_id", device.id)
            .putString("nix_device_name", device.name)
            .apply()
    }

    private fun enableCapture(device: IDeviceCompat) {
        connectButton?.visibility = View.GONE
        captureButton?.visibility = View.VISIBLE
        recycler?.visibility = View.VISIBLE
        disconnectButton?.visibility = View.VISIBLE

        setupCaptureButton(device)
        setupDisconnectButton(device)
    }

    private fun setupDisconnectButton(device: IDeviceCompat) {
        disconnectButton?.setOnClickListener {
            device.disconnect()
            controller.getPreferences().edit()
                .remove("nix_device_id")
                .remove("nix_device_name")
                .apply()
            (context as CollectActivity).getNixSensorHelper().connectedDevice = null
            connectButton?.visibility = View.VISIBLE
            captureButton?.visibility = View.GONE
            recycler?.visibility = View.GONE
            disconnectButton?.visibility = View.GONE
            startNixSearch()
            setupConnectButton()
        }
    }

    private fun setupCaptureButton(device: IDeviceCompat) {
        captureButton?.setOnClickListener {
            Log.d(TAG, "Capture button clicked")
            Log.d(TAG, "Device state: ${device.state}")

            if (device.state != DeviceState.IDLE) {
                Toast.makeText(context, R.string.nix_device_connecting, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            device.measure(object : OnDeviceResultListener {
                override fun onDeviceResult(
                    status: CommandStatus,
                    measurements: Map<ScanMode, IMeasurementData>?
                ) {
                    measurements?.let { m ->
                        for ((mode, data) in m) {
                            Log.d(TAG, "Mode: $mode")
                            Log.d(TAG, "Data: $data")
                            data.toColorInt()?.let { rgb ->
                                Log.d(TAG, "RGB: $rgb")
                                colorList.add(rgb.toString())
                                Log.d(TAG, "Color list size: ${colorList.toImmutableList()}")
                                submitList()
                                recycler?.adapter?.notifyItemInserted(colorList.size - 1)
                                recycler?.scrollToPosition(colorList.size - 1)
                                saveToStorage(rgb.toString())
                            }
                        }
                    }
                }
            })
        }
    }

    private fun submitList() {
        (recycler?.adapter as? ColorAdapter)?.submitList(colorList.map { it }.toImmutableList())
        scrollToLast()
    }

    private fun scrollToLast() {

        try {

            recycler?.postDelayed({

                val pos = recycler?.adapter?.itemCount ?: 1

                recycler?.scrollToPosition(pos - 1)

            }, 500L)

        } catch (e: Exception) {

            e.printStackTrace()

        }
    }

    private fun insertNa() {

        background.launch {

            database.insertObservation(
                currentRange.uniqueId,
                currentTrait.id,
                currentTrait.format,
                "NA",
                (context as? CollectActivity)?.person,
                (context as? CollectActivity)?.locationByPreferences,
                "",
                (context as? CollectActivity)?.studyId,
                null,
                null,
                (context as? CollectActivity)?.rep
            )

            withContext(Dispatchers.Main) {
                colorList.add("NA")
                submitList()
                recycler?.adapter?.notifyItemInserted(colorList.size - 1)
                recycler?.scrollToPosition(colorList.size - 1)
                (context as CollectActivity).refreshRepeatedValuesToolbarIndicator()
            }
        }
    }

    override fun afterLoadExists(act: CollectActivity, value: String?) {
        super.afterLoadExists(act, value)
    }

    override fun afterLoadNotExists(act: CollectActivity) {
        super.afterLoadNotExists(act)
    }

    override fun afterLoadDefault(act: CollectActivity) {
        super.afterLoadDefault(act)
    }

    override fun deleteTraitListener() {
        super.deleteTraitListener()
        val item = getSelected()
        if (item != null) {
            onColorDeleted(item)
        }
    }

    override fun setNaTraitsText() {}

    override fun refreshLayout(onNew: Boolean) {
        super.refreshLayout(false)
    }

    override fun refreshLock() {
        super.refreshLock()
        loadLayout()
    }

    override fun onColorDeleted(position: Int, onDelete: (() -> Unit)?) {

        if (!isDeleting) {

            isDeleting = true

            AlertDialog.Builder(context)
                .setTitle(R.string.delete_color)
                .setMessage(R.string.delete_color_message)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    deleteColorObservation(position, onDelete)
                }
                .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                .setOnDismissListener {
                    isDeleting = false
                }

                .show()
        }
    }

    private fun deleteColorObservation(position: Int, onDelete: (() -> Unit)?) {

        //delete from storage
        background.launch {
            val studyId = collectActivity.studyId
            val plot = (context as? CollectActivity)?.observationUnit
            val traitDbId = currentTrait.id
            val observations = database.getAllObservations(studyId, plot, traitDbId)
            val value = colorList[position]

            if (observations.size > position) {
                val observation = observations[position]
                if (observation.value == value) {
                    database.deleteTrait(studyId, plot, traitDbId, observation.rep)
                }
            }

            withContext(Dispatchers.Main) {
                colorList.removeAt(position)
                submitList()
                recycler?.adapter?.notifyItemRemoved(position)
                (context as CollectActivity).refreshRepeatedValuesToolbarIndicator()
                onDelete?.invoke()
            }
        }
    }

    private fun saveToStorage(
        value: String,
    ) {

        val studyId = collectActivity.studyId
        val person = (context as? CollectActivity)?.person
        val location = (context as? CollectActivity)?.locationByPreferences
        val plot = (context as? CollectActivity)?.observationUnit
        val rep = database.getNextRep(studyId, plot, currentTrait.id)

        background.launch {

            val traitDbId = currentTrait.id

            database.insertObservation(
                plot, traitDbId, type(), value,
                person,
                location, "", studyId,
                null,
                null,
                rep
            )

            withContext(Dispatchers.Main) {
                (context as CollectActivity).refreshRepeatedValuesToolbarIndicator()
            }
        }
    }

    private fun getSelected(): Int? {

        val size = (recycler?.adapter as? ColorAdapter)?.currentList?.size ?: 0
        val lm = recycler?.layoutManager as? LinearLayoutManager

        val lastVisible = lm?.findLastVisibleItemPosition()
        val lastFullVisible = lm?.findLastCompletelyVisibleItemPosition()

        val position = if (lastFullVisible == -1) lastVisible else lastFullVisible

        //ensure position is within array bounds
        if (position != null && position in 0 until size) {

            return position

        }

        return null
    }
}