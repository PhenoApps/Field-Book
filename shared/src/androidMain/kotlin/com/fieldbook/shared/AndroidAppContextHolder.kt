package com.fieldbook.shared

import android.content.Context

object AndroidAppContextHolder {
    lateinit var context: Context
        private set

    @JvmStatic
    fun initialize(context: Context) {
        this.context = context.applicationContext
    }
}
