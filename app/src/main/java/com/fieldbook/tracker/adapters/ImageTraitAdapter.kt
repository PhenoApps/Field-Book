package com.fieldbook.tracker.adapters

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.net.Uri
import android.os.CancellationSignal
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.database.models.ObservationModel
import com.fieldbook.tracker.dialogs.ObservationMetadataFragment

/**
 * Reference:
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 */
class ImageTraitAdapter(
    private val context: Context,
    private val listener: ImageItemHandler,
    private val hasProgressBar: Boolean = false) :
        ListAdapter<ImageTraitAdapter.Model, ImageTraitAdapter.ViewHolder>(DiffCallback()) {

    data class Model(var uri: String, var index: Int)

    interface ImageItemHandler {

        fun onItemClicked(model: Model)

        fun onItemDeleted(model: Model)

    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView =
            view.findViewById(org.phenoapps.androidlibrary.R.id.list_item_image_iv)
        val progressBar: ProgressBar =
            view.findViewById(org.phenoapps.androidlibrary.R.id.list_item_image_pb)
        private val closeButton: ImageButton =
            view.findViewById(org.phenoapps.androidlibrary.R.id.list_item_image_close_btn)

        init {
            // Define click listener for the ViewHolder's View.
            view.setOnClickListener {
                listener.onItemClicked(view.tag as Model)
            }

            view.setOnLongClickListener {
                showObservationMetadataDialog(layoutPosition + 1)
                true
            }

            closeButton.setOnClickListener {
                listener.onItemDeleted(view.tag as Model)
            }

            if (hasProgressBar) {
                progressBar.visibility = View.VISIBLE
            }
        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(org.phenoapps.androidlibrary.R.layout.list_item_image, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        with(currentList[position]) {
            val bmp = decodeBitmap(Uri.parse(this.uri))
            if (bmp != null) viewHolder.progressBar.visibility = View.GONE
            viewHolder.imageView.setImageBitmap(bmp)
            viewHolder.itemView.tag = this
        }
    }

    private fun showObservationMetadataDialog(position: Int?) {
        val currentObservationObject: ObservationModel? = getCurrentObservation(position)
        if (currentObservationObject != null) {
            val dialogFragment: DialogFragment =
                ObservationMetadataFragment().newInstance(currentObservationObject)
            dialogFragment.show((context as CollectActivity).supportFragmentManager, "observationMetadata")
        }
    }

    private fun getCurrentObservation(position: Int?): ObservationModel? {
        val act = context as CollectActivity
        val models = listOf<ObservationModel>(
            *act.getDatabase().getRepeatedValues(
                act.studyId,
                act.observationUnit,
                act.traitDbId
            )
        )
        for (m in models) {
            if (position.toString() == m.rep) {
                return m
            }
        }
        return null
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = currentList.size

    class DiffCallback : DiffUtil.ItemCallback<Model>() {

        override fun areItemsTheSame(oldItem: Model, newItem: Model): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: Model, newItem: Model): Boolean {
            return oldItem == newItem
        }
    }

    private fun decodeBitmap(uri: Uri): Bitmap? {
        return try {

            val signal = CancellationSignal()
            DocumentsContract.getDocumentThumbnail(
                context.contentResolver,
                uri,
                Point(256, 256),
                signal)

        } catch (e: Exception) {

            e.printStackTrace()

            null
        }
    }
}