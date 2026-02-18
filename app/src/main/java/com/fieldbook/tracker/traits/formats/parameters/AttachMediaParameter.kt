package com.fieldbook.tracker.traits.formats.parameters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.repository.TraitRepository
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.ValidationResult
import com.fieldbook.tracker.traits.formats.ui.AttachMediaChoice

class AttachMediaParameter(private val initialDefaultValue: Boolean? = null) :
    BaseFormatParameter(
        nameStringResourceId = R.string.traits_attach_media_title,
        defaultLayoutId = R.layout.list_item_trait_parameter_compose_view,
        parameter = Parameters.ATTACH_MEDIA,
    ) {

    override fun createViewHolder(
        parent: ViewGroup,
    ): BaseFormatParameter.ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_trait_parameter_compose_view, parent, false)
        return ViewHolder(v)
    }

    inner class ViewHolder(itemView: View) : BaseFormatParameter.ViewHolder(itemView) {

        var photoState = mutableStateOf(false)
        var videoState = mutableStateOf(false)
        var audioState = mutableStateOf(false)

        init {

            val composeView = itemView.findViewById<ComposeView>(R.id.compose_view)
            composeView?.apply {

                try {
                    setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                } catch (_: Exception) { }
             }

            composeView.setContent {
                AttachMediaChoice(photoState, videoState, audioState)
            }
         }

        override fun merge(traitObject: TraitObject) = traitObject.apply {
            this.attachPhoto = photoState.value
            this.attachVideo = videoState.value
            this.attachAudio = audioState.value
        }

        override fun load(traitObject: TraitObject?): Boolean {
            try {
                photoState.value = traitObject?.attachPhoto == true
                videoState.value = traitObject?.attachVideo == true
                audioState.value = traitObject?.attachAudio == true
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
            return true
        }

        override fun validate(
            traitRepo: TraitRepository,
            initialTraitObject: TraitObject?
        ) = ValidationResult()
    }
}