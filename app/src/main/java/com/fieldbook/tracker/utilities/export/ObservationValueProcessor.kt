package com.fieldbook.tracker.utilities.export

fun interface ObservationValueProcessor {
    fun processValue(value: String?): Result<String>
    sealed class ProcessException : Exception() {
        object MissingValue : ProcessException()
        object InvalidValue : ProcessException()
        object InvalidFormat : ProcessException()
    }
}

