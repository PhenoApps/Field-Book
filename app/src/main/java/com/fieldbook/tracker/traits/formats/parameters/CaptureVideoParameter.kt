package com.fieldbook.tracker.traits.formats.parameters

import android.app.AlertDialog
import android.view.ViewGroup
import androidx.annotation.StringRes
import com.fieldbook.tracker.R
import com.fieldbook.tracker.enums.traits.ToggleLayoutType
import com.fieldbook.tracker.objects.TraitObject

/**
 * Switches a camera trait between taking photos and recording video. It is locked once the trait
 * has observations, otherwise photos and videos would be mixed under one trait.
 *
 * What changes in video mode is up to the camera, so each format that offers the toggle describes
 * it here rather than in the trait screens.
 *
 * @param noticeTitleRes   shown, with [noticeMessageRes], whenever the user switches to video
 * @param noticeMessageRes tells the user how this camera's videos are stored
 * @param hiddenInVideo    parameters of the same format that do not apply to video
 */
class CaptureVideoParameter(
    @StringRes val noticeTitleRes: Int? = null,
    @StringRes val noticeMessageRes: Int? = null,
    val hiddenInVideo: Set<Parameters> = emptySet()
) : DefaultToggleParameter(
    nameStringResourceId = R.string.traits_capture_video_parameter,
    parameter = Parameters.CAPTURE_VIDEO,
    layoutType = ToggleLayoutType.TRUE_FALSE
) {
    override fun getToggleValue(traitObject: TraitObject?): Boolean =
        traitObject?.captureVideo == true

    override fun setToggleValue(traitObject: TraitObject, value: Boolean) {
        traitObject.captureVideo = value
    }

    override fun createViewHolder(parent: ViewGroup): BaseFormatParameter.ViewHolder {

        val holder = super.createViewHolder(parent) as DefaultToggleParameter.ViewHolder

        val title = noticeTitleRes
        val message = noticeMessageRes

        if (title != null && message != null) {

            //a click listener only fires for the user, unlike a checked listener which load() also trips
            holder.toggleButton.setOnClickListener { button ->
                if (holder.toggleButton.isChecked) {
                    AlertDialog.Builder(button.context, R.style.AppAlertDialog)
                        .setTitle(title)
                        .setMessage(message)
                        .setPositiveButton(R.string.dialog_ok, null)
                        .show()
                }
            }
        }

        return holder
    }
}
