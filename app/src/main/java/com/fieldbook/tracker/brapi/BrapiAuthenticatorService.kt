package com.fieldbook.tracker.brapi

import android.app.Service
import android.content.Intent
import android.os.IBinder

class BrapiAuthenticatorService : Service() {

    private lateinit var authenticator: BrapiAuthenticator

    override fun onCreate() {
        authenticator = BrapiAuthenticator(this)
    }

    override fun onBind(intent: Intent): IBinder = authenticator.iBinder
}
