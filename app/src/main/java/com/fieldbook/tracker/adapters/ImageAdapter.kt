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
        var brapiSynced: Boolean? = null,
        // playback state persisted on the model so it survives view recycling
        var lastPlaybackPosition: Int = 0,
        var isPlaying: Boolean = false
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
                    var thumbBmp: android.graphics.Bitmap?
                    try {
                        val uri = android.net.Uri.parse(model.uri)
                        val cr = view.context.contentResolver
                        val fd = cr.openFileDescriptor(uri, "r")
                        fd?.fileDescriptor?.let { retriever.setDataSource(it) }
                        // get frame at 0ms or closest
                        thumbBmp = retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST)
                    } catch (_: Exception) {
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

                    // Always show thumbnail on bind and clear any previously attached media/listeners.
                    // This prevents VideoView from auto-starting when the view is rebound.
                    try { videoView?.stopPlayback() } catch (_: Exception) {}
                    try { videoView?.setOnPreparedListener(null) } catch (_: Exception) {}
                    try { videoView?.setOnCompletionListener(null) } catch (_: Exception) {}
                    try { videoView?.setOnErrorListener(null) } catch (_: Exception) {}
                    // Reset UI to thumbnail state (do not auto-start playback on bind)
                    videoView?.visibility = View.GONE
                    imageView.visibility = View.VISIBLE
                    playButton?.visibility = View.VISIBLE
                    pauseButton?.visibility = View.GONE
                    // mark model not playing while it's not actively started by user
                    model.isPlaying = false

                    // Helper to reset UI to thumbnail state
                    val resetToThumbnail: () -> Unit = {
                        try {
                            // clear model playback state
                            (itemView.tag as? Model)?.let { it.isPlaying = false; it.lastPlaybackPosition = 0 }
                            currentlyPlayingVideo = null
                            currentlyPlayingHolder = null
                            // stop and clear listeners to fully reset
                            try { videoView?.stopPlayback() } catch (_: Exception) {}
                            try { videoView?.setOnPreparedListener(null) } catch (_: Exception) {}
                            try { videoView?.setOnCompletionListener(null) } catch (_: Exception) {}
                            try { videoView?.setOnErrorListener(null) } catch (_: Exception) {}
                            videoView?.visibility = View.GONE
                            imageView.visibility = View.VISIBLE
                            playButton?.visibility = View.VISIBLE
                            pauseButton?.visibility = View.GONE
                        } catch (_: Exception) {}
                    }

                    // play button starts inline playback using VideoView
                    playButton?.setOnClickListener {
                        try {

                            // if the same video is tracked as currently playing and it's only paused, resume
                            if (currentlyPlayingVideo == videoView && videoView?.isPlaying == false) {
                                // resume from current position (MediaPlayer preserves paused position)
                                videoView.start()
                                (itemView.tag as? Model)?.let { it.isPlaying = true }
                                pauseButton?.visibility = View.VISIBLE
                                playButton.visibility = View.GONE
                                currentlyPlayingVideo = videoView
                                currentlyPlayingHolder = this
                                return@setOnClickListener
                            }

                            // stop any currently playing video first
                            if (currentlyPlayingVideo != null) {
                                try {
                                    currentlyPlayingHolder?.let { holder ->
                                        // save current playback position for the other holder, and pause it
                                        (holder.itemView.tag as? Model)?.let { m ->
                                            try { m.lastPlaybackPosition = holder.videoView?.currentPosition ?: 0 } catch (_: Exception) {}
                                            m.isPlaying = false
                                        }
                                        try { holder.videoView?.pause() } catch (_: Exception) {}
                                        holder.pauseButton?.visibility = View.GONE
                                        holder.playButton?.visibility = View.VISIBLE
                                        holder.videoView?.visibility = View.GONE
                                        holder.imageView.visibility = View.VISIBLE
                                    }
                                } catch (_: Exception) {}
                                currentlyPlayingVideo = null
                                currentlyPlayingHolder = null
                            }

                            val uri = android.net.Uri.parse(model.uri)
                            // prepare video; do not assume it's already prepared from previous binds
                            try { videoView?.stopPlayback() } catch (_: Exception) {}
                            videoView?.setVideoURI(uri)
                            // ensure UI updates for playback start
                            imageView.visibility = View.GONE
                            videoView?.visibility = View.VISIBLE
                            playButton.visibility = View.GONE
                            pauseButton?.visibility = View.VISIBLE

                            // update tracking and start when ready
                            videoView?.setOnPreparedListener { mp ->
                                mp.isLooping = false
                                // seek to persisted position if any
                                val pos = (itemView.tag as? Model)?.lastPlaybackPosition ?: 0
                                if (pos > 0) videoView.seekTo(pos)
                                videoView.start()
                                // mark model playing
                                (itemView.tag as? Model)?.isPlaying = true
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
                                // persist current position and pause without fully stopping playback
                                try { (itemView.tag as? Model)?.lastPlaybackPosition = videoView.currentPosition } catch (_: Exception) {}
                                (itemView.tag as? Model)?.isPlaying = false
                                videoView.pause()
                                pauseButton.visibility = View.GONE
                                playButton?.visibility = View.VISIBLE
                                // keep videoView visible but paused (user can resume)
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
                // persist paused position so it survives recycling
                try {
                    val model = holder.itemView.tag as? Model
                    model?.let {
                        try { it.lastPlaybackPosition = holder.videoView?.currentPosition ?: it.lastPlaybackPosition } catch (_: Exception) {}
                        it.isPlaying = holder.videoView?.isPlaying == true
                    }
                } catch (_: Exception) {}

                // pause video (don't call stopPlayback here to preserve position) and clear listeners
                try { holder.videoView?.pause() } catch (_: Exception) {}
                try { holder.videoView?.setOnPreparedListener(null) } catch (_: Exception) {}
                try { holder.videoView?.setOnCompletionListener(null) } catch (_: Exception) {}
                try { holder.videoView?.setOnErrorListener(null) } catch (_: Exception) {}
                holder.videoView?.visibility = View.GONE
                holder.imageView.visibility = View.VISIBLE
                holder.playButton?.visibility = View.VISIBLE
                holder.pauseButton?.visibility = View.GONE
                if (currentlyPlayingHolder == holder) {
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