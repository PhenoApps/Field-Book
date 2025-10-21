package com.fieldbook.tracker.activities

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.add
import androidx.fragment.app.commit
import com.fieldbook.tracker.R
import com.fieldbook.tracker.fragments.CropImageFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CropImageActivity: ThemedActivity() {

    companion object {
        const val TAG = "CropImageActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_crop_image)
        if (savedInstanceState == null) {

            val bundle = bundleOf(
                CropImageFragment.EXTRA_TRAIT_ID to intent.getIntExtra(CropImageFragment.EXTRA_TRAIT_ID, -1),
                CropImageFragment.EXTRA_IMAGE_URI to intent.getStringExtra(CropImageFragment.EXTRA_IMAGE_URI),
            )

            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add<CropImageFragment>(R.id.fragment_container_view, args = bundle)
            }
        }

        onBackPressedDispatcher.addCallback(this, standardBackCallback())
    }
}