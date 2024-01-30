package com.fieldbook.tracker.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import com.fieldbook.tracker.R

open class BorderedDialog(ctx: Context, themeResId: Int) : Dialog(ctx, themeResId) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.setBackgroundDrawableResource(R.drawable.inset_border)
    }
}