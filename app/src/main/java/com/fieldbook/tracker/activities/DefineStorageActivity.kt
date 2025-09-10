package com.fieldbook.tracker.activities

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import com.fieldbook.tracker.R
import org.phenoapps.utils.BaseDocumentTreeUtil

class DefineStorageActivity: ThemedActivity() {

    private var mBackButtonEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_define_storage)
        setupBackCallback()
    }

    private fun setupBackCallback() {
        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (mBackButtonEnabled) {
                    val result = if (BaseDocumentTreeUtil.isEnabled(this@DefineStorageActivity)) RESULT_OK else RESULT_CANCELED
                    setResult(result)
                    finish()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)
    }

    fun enableBackButton(enable: Boolean) {
        mBackButtonEnabled = enable
    }
}