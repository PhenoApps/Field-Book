package com.fieldbook.shared

import com.fieldbook.shared.sqldelight.DriverFactory

object AppContext {
    private var driverFactory: DriverFactory? = null

    fun init(driverFactory: DriverFactory) {
        this.driverFactory = driverFactory
    }

    fun driverFactory(): DriverFactory = driverFactory
        ?: throw IllegalStateException("AppContext not initialized. Call AppContext.init(...) before using it.")
}
