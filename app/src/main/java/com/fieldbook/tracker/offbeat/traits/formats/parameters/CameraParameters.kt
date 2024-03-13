package com.fieldbook.tracker.offbeat.traits.formats.parameters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.get
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.offbeat.traits.formats.ValidationResult

class CameraParameters : BaseFormatParameter(
    nameStringResourceId = R.string.traits_create_camera,
    defaultLayoutId = R.layout.list_item_trait_parameter_camera,
    parameter = Parameters.CAMERA
) {

    override fun createViewHolder(
        parent: ViewGroup,
    ): BaseFormatParameter.ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_trait_parameter_camera, parent, false)
        return ViewHolder(v)
    }

    class ViewHolder(itemView: View) : BaseFormatParameter.ViewHolder(itemView) {

        private val radioGroup: RadioGroup =
            itemView.findViewById(R.id.list_item_trait_parameter_camera_rg)

        init {

            //programmatically add radio button from camera types
            CameraTypes.entries.forEach {

                val radioButton = RadioButton(itemView.context)

                radioButton.id = it.ordinal

                radioButton.text = it.format.getName(itemView.context)

                radioButton.setCompoundDrawablesWithIntrinsicBounds(
                    AppCompatResources.getDrawable(itemView.context, it.format.getIcon()),
                    null,
                    null,
                    null,
                )

                if (it == CameraTypes.DEFAULT) {

                    radioButton.isChecked = true

                }

                radioGroup.addView(radioButton)
            }
        }

        override fun merge(traitObject: TraitObject) = traitObject.apply {

            CameraTypes.entries.forEach { cameraType ->

                val radioButton = radioGroup[cameraType.ordinal] as RadioButton

                if (radioButton.isChecked) {

                    val name = cameraType.format.getDatabaseName(itemView.context)

                    format = name

                }
            }
        }

        override fun load(traitObject: TraitObject?): Boolean {
            try {
                traitObject?.format?.let { format ->

                    CameraTypes.entries.forEach { cameraType ->

                        if (cameraType.format.getDatabaseName(itemView.context) == format) {

                            (radioGroup[cameraType.ordinal] as RadioButton).isChecked = true
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
            return true
        }

        override fun validate(
            database: DataHelper,
            initialTraitObject: TraitObject?
        ) = ValidationResult()
    }
}