package com.fieldbook.tracker.interfaces

import android.content.Context
import android.os.Handler
import com.fieldbook.tracker.objects.GeoNavHelper
import com.fieldbook.tracker.utilities.GnssThreadHelper
import com.fieldbook.tracker.utilities.SoundHelperImpl
import com.fieldbook.tracker.views.CollectInputView
import com.fieldbook.tracker.views.RangeBoxView
import com.fieldbook.tracker.views.TraitBoxView
import org.phenoapps.security.SecureBluetoothActivityImpl

interface CollectController: FieldController {
    fun getRangeBox(): RangeBoxView
    fun getTraitBox(): TraitBoxView
    fun getInputView(): CollectInputView
    fun getSoundHelper(): SoundHelperImpl
    fun resetGeoNavMessages()
    fun getGeoNavHelper(): GeoNavHelper
    fun getSecurityChecker(): SecureBluetoothActivityImpl
    fun getGnssThreadHelper(): GnssThreadHelper
    fun getAverageHandler(): Handler?
    fun moveToSearch(command: String,
                     plotIndices: IntArray?,
                     rangeId: String?,
                     plotId: String?,
                     data: String?,
                     traitIndex: Int): Boolean
    fun queryForLabelValue(
        plotId: String, label: String, isAttribute: Boolean?
    ) : String
}