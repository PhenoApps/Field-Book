package com.fieldbook.tracker.vuzix

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.fieldbook.tracker.database.dao.ObservationVariableDao
import com.fieldbook.tracker.vuzix.BluetoothServer.ACTION_STATUS
import com.fieldbook.tracker.vuzix.BluetoothServer.EXTRA_VOICE_COMMAND

class VoiceCommandReceiver(private val controller: VuzixController) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == BluetoothServer.ACTION_VOICE_COMMAND) {
            intent.extras?.getString(EXTRA_VOICE_COMMAND)?.split("\r\n")?.forEach { cmd ->
                if (cmd.isNotBlank()) {
                    Log.d("VoiceCommand", cmd)
                    dataAction(cmd)
                }
            }
        }
    }

    private fun dataAction(data: String) {

        try {
            if (data.isNotBlank()) {
                val json = VuzixJsonMessage(data)
                if (VuzixJsonMessage.COMMAND_KEY in json.keys()) {
                    when (json.get(VuzixJsonMessage.COMMAND_KEY)?.toIntOrNull() ?: 0) {
                        VuzixJsonMessage.Companion.Commands.NextPlot.ordinal -> controller.movePlot("right")
                        VuzixJsonMessage.Companion.Commands.PrevPlot.ordinal -> controller.movePlot("left")
                        VuzixJsonMessage.Companion.Commands.NextTrait.ordinal -> controller.moveTrait("right")
                        VuzixJsonMessage.Companion.Commands.PrevTrait.ordinal -> controller.moveTrait("left")
                        VuzixJsonMessage.Companion.Commands.SelectTrait.ordinal -> {
                            json.get(VuzixJsonMessage.COMMAND_KEY_VALUE).let { cmd ->
                                val traits = ObservationVariableDao.getAllTraitObjects()
                                for (trait in traits) {
                                    //vuzix replaces spaces with underscores
                                    if (trait.trait != null && trait.trait == cmd?.replace("_", " ")?.trim { it <= ' ' }) {
                                        val pos = trait.realPosition
                                        controller.selectTrait(pos - 1)
                                        break
                                    }
                                }
                            }
                        }
                        VuzixJsonMessage.Companion.Commands.EnterValue.ordinal -> {
                            json.get(VuzixJsonMessage.COMMAND_KEY_VALUE)?.let { value ->
                                controller.setEditTextCurrentValue(value)
                            }
                        }
                        VuzixJsonMessage.Companion.Commands.Disconnect.ordinal -> {
                            val intent = Intent()
                            intent.action = ACTION_STATUS
                            controller.getContext().sendBroadcast(intent)
                        }
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}