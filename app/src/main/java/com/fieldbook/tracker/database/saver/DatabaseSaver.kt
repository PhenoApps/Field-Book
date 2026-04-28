package com.fieldbook.tracker.database.saver

import kotlin.reflect.KProperty

interface DatabaseSaver<I, O> {
    fun saveData(requiredData: I): Result<O>
    //operator fun getValue(thisRef: Any?, property: KProperty<*>): DatabaseSaver<I, O> = this
}