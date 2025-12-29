package com.fieldbook.tracker.ui

import android.app.Dialog
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.fieldbook.tracker.R
import com.fieldbook.tracker.utilities.Utils

class MediaPreviewDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_MEDIA_TYPE = "arg_media_type"
        private const val ARG_MEDIA_PATH = "arg_media_path"
        private const val ARG_OBS_ID = "obs_id"

        fun newInstance(obsId: String, mediaType: String, mediaPath: String): MediaPreviewDialogFragment {
            val f = MediaPreviewDialogFragment()
            val b = Bundle()
            b.putString(ARG_MEDIA_TYPE, mediaType)
            b.putString(ARG_MEDIA_PATH, mediaPath)
            b.putString(ARG_OBS_ID, obsId)

            f.arguments = b
            return f
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val mediaType = arguments?.getString(ARG_MEDIA_TYPE) ?: "photo"
        val mediaPath = arguments?.getString(ARG_MEDIA_PATH) ?: ""
        val obsId = arguments?.getString(ARG_OBS_ID) ?: ""

        if (obsId.isEmpty()) {
            Utils.makeToast(context, getString(R.string.no_observation))
            dismiss()
        }

        val title = when {
            mediaType.startsWith("photo") -> getString(R.string.trait_photo_tts_success)
            mediaType == "audio" -> getString(R.string.field_audio_recording_stop)
            mediaType == "video" -> getString(R.string.video_recording_saved)
            else -> getString(R.string.trait_photo_tts_success)
        }

        // create preview compose view with delete button hidden
        val preview = createMediaPreviewComposeView(requireContext(), mediaPath, if (mediaType.startsWith("photo")) "photo" else if (mediaType == "video") "video" else "audio")

        // wrap preview in a container and attach viewtree owners to ensure Compose can find them
        val container = FrameLayout(requireContext())
        container.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val pad = (resources.displayMetrics.density * 8).toInt()
        container.setPadding(pad, pad, pad, pad)

        // Attach lifecycle and SavedStateRegistry owners so ComposeView inside dialog can resolve them.
        container.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) {
                try {
                    val root = v.rootView
                    root.setViewTreeLifecycleOwner(requireActivity())
                } catch (_: Exception) {}

                try {
                    val root = v.rootView
                    root.setViewTreeSavedStateRegistryOwner(requireActivity())
                } catch (_: Exception) {}
            }

            override fun onViewDetachedFromWindow(v: android.view.View) {}
        })

        container.addView(preview)

        val builder = AlertDialog.Builder(requireContext(), R.style.AppAlertDialog)
            .setTitle(title)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                (activity as? com.fieldbook.tracker.activities.CollectActivity)?.onMediaConfirmFromDialog(obsId, mediaType, mediaPath)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                (activity as? com.fieldbook.tracker.activities.CollectActivity)?.onMediaCancelFromDialog(mediaPath)
            }

        val d = builder.create()

        d.setOnShowListener {
            try {
                val params = d.window?.attributes
                params?.width = (resources.displayMetrics.widthPixels * 0.9).toInt()
                params?.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT
                params?.gravity = android.view.Gravity.CENTER
                d.window?.attributes = params
            } catch (_: Exception) {}
        }

        return d
    }
}