package com.fieldbook.tracker.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.fieldbook.tracker.R

class GallerySlideFragment : Fragment() {

    lateinit var images: IntArray

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        images = intArrayOf(
            R.drawable.field_book_mini_numeric,
            R.drawable.field_book_mini_categorical,
            R.drawable.field_book_mini_percent,
            //R.drawable.field_book_mini_photo,
            //R.drawable.field_book_mini_location,
            //R.drawable.field_book_mini_counter,
            //R.drawable.field_book_intro_barcode,
            R.drawable.field_book_intro_brapi,
        )
        return inflater.inflate(R.layout.app_intro_gallery_slide, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title = view.findViewById<TextView>(R.id.title)
        val description = view.findViewById<TextView>(R.id.description)
        val gridLayout = view.findViewById<GridLayout>(R.id.grid_layout)

        title?.text = getString(R.string.app_intro_intro_title_slide2)
        description?.text = getString(R.string.app_intro_intro_summary_slide2)


        images.let { drawables ->
            // set # of rows and cols
            gridLayout.rowCount = 2
            gridLayout.columnCount = 2

            drawables.forEachIndexed { index, drawable ->
                val imageView = ImageView(context).apply {
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = 0
                        height = 0
                        // assign (row, col) position for an image
                        rowSpec = GridLayout.spec(index / 2, 1f)
                        columnSpec = GridLayout.spec(index % 2, 1f)
                        setMargins(16, 16, 16, 16)
                    }
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageDrawable(ContextCompat.getDrawable(requireContext(), drawable))
                }
                gridLayout.addView(imageView)
            }
        }
    }

    companion object {
        fun newInstance(): GallerySlideFragment {
            return GallerySlideFragment()
        }

    }
}