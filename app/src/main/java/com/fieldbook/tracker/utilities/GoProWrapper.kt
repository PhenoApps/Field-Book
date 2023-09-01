package com.fieldbook.tracker.utilities

import android.content.Context
import android.graphics.Bitmap
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.traits.BaseTraitLayout
import com.fieldbook.tracker.traits.GoProTraitLayout
import dagger.hilt.android.qualifiers.ActivityContext
import org.phenoapps.fragments.gopro.GoProGatt
import org.phenoapps.fragments.gopro.GoProHelper
import javax.inject.Inject

class GoProWrapper @Inject constructor(
    @ActivityContext private val context: Context
) :
    GoProGatt.GoProGattController,
    GoProHelper.OnGoProStreamReady {

    private val activity: CollectActivity by lazy { context as CollectActivity }

    var helper: GoProHelper? = null

    val gatt: GoProGatt by lazy { GoProGatt(this) }

    fun attach() {
        //activity.advisor().connectWith {
            helper = GoProHelper(activity, this)
        //}
    }

    fun destroy() {
        try {
            helper?.onDestroy()
            gatt.clear()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onApRequested() {
        //not used
    }

    override fun onCredentialsAcquired() {

        if (helper != null) {
            if (helper?.checkWifiEnabled() != true) {
                helper?.enableWifi()
            }
        }

        activity.runOnUiThread {
            val goProTrait: BaseTraitLayout =
                activity.getTraitLayouts().getTraitLayout(GoProTraitLayout.type)
            if (goProTrait is GoProTraitLayout) {
                goProTrait.onCredentialsAcquired()
            }
        }
    }

    override fun onImageRequestReady(bitmap: Bitmap, data: Map<String, String>) {
        activity.runOnUiThread {
            val goProTrait: BaseTraitLayout =
                activity.getTraitLayouts().getTraitLayout(GoProTraitLayout.type)
            if (goProTrait is GoProTraitLayout) {
                goProTrait.onImageRequestReady(bitmap, data)
            }
        }
    }

    override fun onStreamReady() {
        activity.runOnUiThread {
            val goProTrait: BaseTraitLayout =
                activity.getTraitLayouts().getTraitLayout(GoProTraitLayout.type)
            if (goProTrait is GoProTraitLayout) {
                goProTrait.onStreamReady()
            }
        }
    }

    override fun onModelName(modelName: String) {
        activity.runOnUiThread {
            val goProTrait: BaseTraitLayout =
                activity.getTraitLayouts().getTraitLayout(GoProTraitLayout.type)
            if (goProTrait is GoProTraitLayout) {
                goProTrait.onModelName(modelName)
            }
        }
    }

    override fun onBoardType(boardType: String) {}
    override fun onBssid(wifiBSSID: String) {}
    override fun onFirmware(firmware: String) {}
    override fun onModelId(modelID: Int) {}
    override fun onSerialNumber(serialNumber: String) {}
    override fun onSsid(wifiSSID: String) {}

}