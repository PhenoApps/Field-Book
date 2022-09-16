package com.fieldbook.tracker.activities

import android.app.Activity
import android.os.Bundle
import com.fieldbook.tracker.R
import org.phenoapps.utils.BaseDocumentTreeUtil

class DefineStorageActivity: ThemedActivity() {

    private var mBackButtonEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_define_storage)
    }

    override fun onBackPressed() {

        if (mBackButtonEnabled) {
            super.onBackPressed()
            setResult(if (BaseDocumentTreeUtil.isEnabled(this)) {
                Activity.RESULT_OK
            } else Activity.RESULT_CANCELED)

            finish()
        }
    }

    fun enableBackButton(enable: Boolean) {
        mBackButtonEnabled = enable
    }
}