package com.fieldbook.tracker.traits.formats.presenters

import android.content.Context
import com.fieldbook.tracker.objects.TraitObject

/**
 * The role of ValuePresenter is to take an observation value's model and present it in a way
 * that can be displayed to the user.
 */
interface ValuePresenter {

    /**
     * Represent the value in a way that can be displayed to the user.
     * @param context The context of the application.
     * @param value The value to represent.
     * @return The value represented as a string.
     */
    fun represent(context: Context, value: Any, trait: TraitObject? = null): String

}