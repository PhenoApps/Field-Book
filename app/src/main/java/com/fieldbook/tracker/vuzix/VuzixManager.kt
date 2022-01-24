package com.fieldbook.tracker.vuzix

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.database.dao.ObservationDao.Companion.getFieldBookObservation
import com.fieldbook.tracker.database.dao.ObservationVariableDao.Companion.getAllTraitObjects
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.PrefsConstants
import org.json.JSONObject
import java.lang.Exception
import java.lang.NullPointerException
import java.util.NoSuchElementException

class VuzixManager(private val controller: VuzixController) {

    private val mVuzixMonitor = DataMonitor(controller.getContext())
    private var mVoiceCommandReceiver: VoiceCommandReceiver? = null
    private val mVuzixRelayThreadHandler = HandlerThread("vuzix relay")

    private val mPrefs by lazy {
        controller.getContext().getSharedPreferences("Settings", Context.MODE_PRIVATE)
    }

    private val mVuzixHandler: Handler
    private var mCompleted = false

    init {

        mVuzixRelayThreadHandler.start()
        mVuzixRelayThreadHandler.looper

        mVuzixHandler = Handler(mVuzixRelayThreadHandler.looper)

        relayFieldBookToVuzix()
    }

    private fun relayFieldBookToVuzix() {

        if (!mCompleted) {

            try {

                mVuzixHandler.postDelayed({

                    try {

                        val prefixes = controller.getPrefixData()
                        val plotId = controller.getPlotId()
                        val trait = controller.getCurrentTrait()
                        val expId = controller.getStudyId()

                        if (expId == mPrefs.getInt(PrefsConstants.SELECTED_FIELD_ID, 0).toString()) {

                            var name = ""
                            if (trait.trait != null) name = trait.trait

                            var traitValue: String = controller.getEditTextCurrentValue()

                            try {

                                val model = getFieldBookObservation(expId, plotId, name)

                                if (model?.value != null) {
                                    traitValue = model.value ?: "None"
                                }

                            } catch (e: NoSuchElementException) {

                                traitValue = "None"

                                //e.printStackTrace();
                            } catch (e: Exception) {

                                Log.d(
                                    CollectActivity.TAG,
                                    "Unknown database error occurred retrieving observation."
                                )

                                traitValue = "None"

                                e.printStackTrace()
                            }

                            val json = VuzixJsonMessage()

                            if (prefixes.size > 1) {
                                json.put(VuzixJsonMessage.FIRST_PREFIX_KEY, prefixes[0])
                                json.put(VuzixJsonMessage.FIRST_PREFIX_VALUE_KEY, prefixes[1])
                            }

                            if (prefixes.size > 3) {
                                json.put(VuzixJsonMessage.SECOND_PREFIX_KEY, prefixes[2])
                                json.put(VuzixJsonMessage.SECOND_PREFIX_VALUE_KEY, prefixes[3])
                            }

                            json.put(VuzixJsonMessage.TRAIT_NAME_KEY, name)
                            json.put(VuzixJsonMessage.TRAIT_VALUE_KEY, traitValue)
                            json.put(VuzixJsonMessage.TRAIT_FORMAT_KEY, trait.format)
                            json.put(VuzixJsonMessage.TRAITS_KEY, getAllTraitObjects()
                                .toArray(arrayOf<TraitObject>()).mapNotNull { it.trait }
                                .toTypedArray())
                            json.put(VuzixJsonMessage.COMMAND_KEY, "UPDATE")
                            json.put(VuzixJsonMessage.COMMAND_KEY_VALUE, "*")
                            mVuzixMonitor.pushExtra(DataMonitor.EXTRA_DATA, json.toString())

                            val interval: Int = mPrefs.getString(GeneralKeys.VUZIX_INTERVAL, "1")?.toInt() ?: 1

                            val start = System.nanoTime()
                            while ((System.nanoTime() - start) * 1e-9 < interval) {
                                //noop
                            }

                            relayFieldBookToVuzix()
                        }

                    } catch (e: Exception) {

                        e.printStackTrace()

                    } catch (npe: NullPointerException) {

                        //noop

                    } finally {

                        relayFieldBookToVuzix()

                    }
                }, 0L)

            } catch (e: Exception) {

                e.printStackTrace()

            }
        }
    }

    fun register() {
        try {
            with(controller.getContext()) {
                startService(Intent(this, DataTransferService::class.java))
                mVoiceCommandReceiver = VoiceCommandReceiver(controller)
                val intentFilter = IntentFilter().apply {
                    addAction(BluetoothServer.ACTION_VOICE_COMMAND)
                }
                registerReceiver(mVoiceCommandReceiver, intentFilter)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun unregister() {

        try {
            mCompleted = true
            with(controller.getContext()) {
                mVuzixRelayThreadHandler.quit()
                unregisterReceiver(mVoiceCommandReceiver)
                stopService(Intent(this, DataTransferService::class.java))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}