package com.fieldbook.tracker.activities.brapi.io.sync

class BrapiException(val code: Int?) : Exception("BrAPI operation failed with code: $code")
