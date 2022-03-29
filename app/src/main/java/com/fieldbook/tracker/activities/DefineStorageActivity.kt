package com.fieldbook.tracker.activities

import android.app.Activity
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.fieldbook.tracker.R
import com.fieldbook.tracker.utilities.DocumentTreeUtil

class DefineStorageActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_define_storage)
    }

    override fun onBackPressed() {
        super.onBackPressed()

        setResult(if (DocumentTreeUtil.isEnabled(this)) {
            Activity.RESULT_OK
        } else Activity.RESULT_CANCELED)

        finish()
    }
}