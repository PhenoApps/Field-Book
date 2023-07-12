package com.fieldbook.tracker.interfaces

import android.os.HandlerThread
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
    fun getSecurityChecker(): SecureBluetoothActivityImpl
    fun getAverageHandler(): HandlerThread
    fun moveToSearch(command: String,
                     plotIndices: IntArray?,
                     rangeId: String?,
                     plotId: String?,
                     data: String?,
                     traitIndex: Int): Boolean
}