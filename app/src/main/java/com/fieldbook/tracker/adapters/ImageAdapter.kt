package com.fieldbook.tracker.adapters

import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.camera.view.PreviewView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.utilities.BitmapLoader
import java.io.FileNotFoundException


/**
 * Reference:
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 *
 * The ImageAdapter is used in the abstract camera trait view. It is used to display a horizontal
 * scrolling list of images, each with a close button to delete the image. The last viewable list item
 * is a camerax preview view or an image view, depending on the actual implementation. By default, for system/camerax
 * the preview view is used, which has a shutter button, a settings button, and an 'embiggen' button that
 * starts a fullscreen capture.
 */
class ImageAdapter(private val context: Context, private val listener: ImageItemHandler) :
        ListAdapter<ImageAdapter.Model, ImageAdapter.ViewHolder>(DiffCallback()) {

    enum class Type {
        IMAGE,
        PREVIEW
    }

    data class Model(
        val id: Int,
        val type: Type = Type.IMAGE,
        val orientation: Int = Configuration.ORIENTATION_PORTRAIT,
        var uri: String? = null,
        var brapiSynced: Boolean? = null
    )

    interface ImageItemHandler {

        fun onItemClicked(model: Model)

        fun onItemDeleted(model: Model)

        fun onItemLongClicked(model: Model)

    }

    abstract class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        abstract fun bind(model: Model)
    }

    // Keep track of currently playing VideoView and its ViewHolder so we can pause/stop when necessary
    private var currentlyPlayingVideo: android.widget.VideoView? = null
    private var currentlyPlayingHolder: ImageViewHolder? = null

    inner class ImageViewHolder(private val view: View) : ViewHolder(view) {

        val cardView: CardView = view.findViewById(R.id.list_item_image_cv)
        val imageView: ImageView = view.findViewById(R.id.list_item_image_iv)
        val closeButton: ImageButton = view.findViewById(R.id.list_item_image_close_btn)
        val labelView: TextView = view.findViewById(R.id.list_item_image_label_tv)
        val videoView: android.widget.VideoView? = view.findViewById(R.id.list_item_video_vv)
        val playButton: ImageButton? = view.findViewById(R.id.list_item_play_btn)
        val pauseButton: ImageButton? = view.findViewById(R.id.list_item_pause_btn)

        init {

            view.setOnLongClickListener {
                listener.onItemLongClicked(view.tag as Model)
                true
            }

            closeButton.setOnClickListener {
                listener.onItemDeleted(view.tag as Model)
            }
        }

        override fun bind(model: Model) {

            itemView.tag = model

            labelView.visibility = View.GONE

            try {

                val (actualWidth, actualHeight) =
                    if (model.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        (view.context.resources.getDimensionPixelSize(R.dimen.camera_preview_landscape_width)
                                to view.context.resources.getDimensionPixelSize(R.dimen.camera_preview_landscape_height))
                    } else (view.context.resources.getDimensionPixelSize(R.dimen.camera_preview_portrait_height)
                            to view.context.resources.getDimensionPixelSize(R.dimen.camera_preview_portrait_height))

                (cardView.layoutParams as ConstraintLayout.LayoutParams).apply {
                    width = actualWidth
                    height = actualHeight
                }

                val isVideo = model.uri?.lowercase()?.endsWith(".mp4") == true || (model.uri?.startsWith("content://") == true && view.context.contentResolver.getType(android.net.Uri.parse(model.uri))?.startsWith("video/") == true)

                //set or clear the click listener, videos should not open the external viewer
                if (isVideo) {
                    view.setOnClickListener(null)
                } else {
                    view.setOnClickListener { listener.onItemClicked(model) }
                }

                if (isVideo) {
                    // Extract a frame using MediaMetadataRetriever for the first frame as thumbnail
                    val retriever = android.media.MediaMetadataRetriever()
                    var thumbBmp: android.graphics.Bitmap? = null
                    try {
                        val uri = android.net.Uri.parse(model.uri)
                        val cr = view.context.contentResolver
                        val fd = cr.openFileDescriptor(uri, "r")
                        fd?.fileDescriptor?.let { retriever.setDataSource(it) }
                        // get frame at 0ms or closest
                        thumbBmp = retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST)
                    } catch (e: Exception) {
                        // ignore and fallback
                        thumbBmp = null
                    } finally {
                        try { retriever.release() } catch (_: Exception) {}
                    }

                    if (thumbBmp != null) {
                        imageView.setImageBitmap(thumbBmp)
                    } else if (model.uri == "NA") {
                        val data = context.resources.assets.open("na_placeholder.jpg").readBytes()
                        val bmp = BitmapFactory.decodeByteArray(data, 0, data.size)
                        imageView.setImageBitmap(bmp)
                    } else {
                        // if no thumbnail, just clear imageView background to a neutral color
                        imageView.setImageDrawable(null)
                    }

                    // Ensure videoView is hidden initially
                    videoView?.visibility = View.GONE
                    playButton?.visibility = View.VISIBLE
                    pauseButton?.visibility = View.GONE

                    // Helper to reset UI to thumbnail state
                    val resetToThumbnail: () -> Unit = {
                        try {
                            currentlyPlayingVideo = null
                            currentlyPlayingHolder = null
                            videoView?.visibility = View.GONE
                            imageView.visibility = View.VISIBLE
                            playButton?.visibility = View.VISIBLE
                            pauseButton?.visibility = View.GONE
                            videoView?.stopPlayback()
                        } catch (_: Exception) {}
                    }

                    // play button starts inline playback using VideoView
                    playButton?.setOnClickListener {
                        try {
                            // stop any currently playing video first
                            if (currentlyPlayingVideo != null && currentlyPlayingVideo !== videoView) {
                                try {
                                    currentlyPlayingHolder?.let { holder ->
                                        holder.pauseButton?.visibility = View.GONE
                                        holder.playButton?.visibility = View.VISIBLE
                                        holder.videoView?.visibility = View.GONE
                                        holder.imageView.visibility = View.VISIBLE
                                        holder.videoView?.stopPlayback()
                                    }
                                } catch (_: Exception) {}
                                currentlyPlayingVideo = null
                                currentlyPlayingHolder = null
                            }

                            val uri = android.net.Uri.parse(model.uri)
                            videoView?.setVideoURI(uri)
                            imageView.visibility = View.GONE
                            videoView?.visibility = View.VISIBLE
                            // update UI to show pause
                            playButton.visibility = View.GONE
                            pauseButton?.visibility = View.VISIBLE

                            videoView?.setOnPreparedListener { mp ->
                                mp.isLooping = false
                                videoView.start()
                                // track current playing video
                                currentlyPlayingVideo = videoView
                                currentlyPlayingHolder = this
                            }

                            videoView?.setOnCompletionListener {
                                // reset to thumbnail view
                                resetToThumbnail()
                            }

                            videoView?.setOnErrorListener { _, _, _ ->
                                // on error, restore click behavior to open external viewer
                                resetToThumbnail()
                                view.setOnClickListener { listener.onItemClicked(model) }
                                true
                            }

                        } catch (e: Exception) {
                            e.printStackTrace()
                            view.setOnClickListener { listener.onItemClicked(model) }
                        }
                    }

                    // pause button pauses playback and toggles UI
                    pauseButton?.setOnClickListener {
                        try {
                            if (videoView?.isPlaying == true) {
                                videoView.pause()
                                pauseButton.visibility = View.GONE
                                playButton?.visibility = View.VISIBLE
                                // keep videoView visible but paused (user can resume)
                            } else {
                                // resume
                                videoView?.start()
                                pauseButton.visibility = View.VISIBLE
                                playButton?.visibility = View.GONE
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                } else {

                    try {

                        val preview = if (model.uri == "NA") {
                            // show NA placeholder only when value is literally "NA"
                            val data = context.resources.assets.open("na_placeholder.jpg").readBytes()
                            BitmapFactory.decodeByteArray(data, 0, data.size)
                        } else if (model.uri?.contains("content://") != true) {
                            // external/no content URI: show nothing or placeholder label
                            null
                        } else {
                            BitmapLoader.getPreview(view.context, model.uri, model.orientation)
                        }

                        preview?.let { imageView.setImageBitmap(it) } ?: imageView.setImageDrawable(null)
                        videoView?.visibility = View.GONE
                        playButton?.visibility = View.GONE
                        pauseButton?.visibility = View.GONE

                    } catch (f: FileNotFoundException) {

                        f.printStackTrace()

                    }
                }

            } catch (f: FileNotFoundException) {

                f.printStackTrace()

            }
        }
    }

    class PreviewViewHolder(view: View) : ViewHolder(view) {

        val previewView: PreviewView = view.findViewById(R.id.trait_camera_pv)
        val embiggenButton: ImageButton = view.findViewById(R.id.trait_camera_expand_btn)

        override fun bind(model: Model) {

            try {
                previewView.visibility = View.VISIBLE
                embiggenButton.visibility = View.VISIBLE
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return currentList[position].type.ordinal
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {

        // Create a new view, which defines the UI of the list item
        return when (viewType) {

            //inflate and create the preview view list item
            Type.PREVIEW.ordinal -> {

                val view = LayoutInflater.from(viewGroup.context)
                    .inflate(R.layout.list_item_camera_preview, viewGroup, false)

                PreviewViewHolder(view)
            }

            else -> {

                //inflate and create the image view list item
                val view = LayoutInflater.from(viewGroup.context)
                    .inflate(R.layout.list_item_image, viewGroup, false)

                ImageViewHolder(view)
            }
        }
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        with(currentList[position]) {
            viewHolder.bind(this)
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = currentList.size

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        // If a ViewHolder with playing video is recycled, stop playback and reset the UI
        if (holder is ImageViewHolder) {
            try {
                if (currentlyPlayingHolder == holder) {
                    try {
                        holder.videoView?.stopPlayback()
                    } catch (_: Exception) {}
                    holder.videoView?.visibility = View.GONE
                    holder.imageView.visibility = View.VISIBLE
                    holder.playButton?.visibility = View.VISIBLE
                    holder.pauseButton?.visibility = View.GONE
                    currentlyPlayingVideo = null
                    currentlyPlayingHolder = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Model>() {

        override fun areItemsTheSame(oldItem: Model, newItem: Model): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: Model, newItem: Model): Boolean {
            return oldItem == newItem
        }
    }
}