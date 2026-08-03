package com.fieldbook.tracker.traits

import android.content.Context
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.views.CollectInputView

/**
 * Node / sidecar session: edits go to [onValueChange], never to Collect's plot trait row.
 */
class NodeTraitValueSession(
    context: Context,
    private val trait: TraitObject,
    initialValue: String,
    private var locked: Boolean,
    private var onValueChange: (String) -> Unit,
) : TraitValueSession {

    private val buffer: CollectInputView = CollectInputView.createDetached(context).also {
        it.text = initialValue
    }

    override fun currentTrait(): TraitObject = trait
    override fun isLocked(): Boolean = locked
    override fun inputView(): CollectInputView = buffer

    override fun commit(trait: TraitObject, value: String) {
        buffer.text = value
        if (!locked) onValueChange(value)
    }

    override fun clear(trait: TraitObject) {
        buffer.text = ""
        if (!locked) onValueChange("")
    }

    fun rebind(value: String, locked: Boolean, onValueChange: (String) -> Unit) {
        this.locked = locked
        this.onValueChange = onValueChange
        syncFromExternal(value)
    }

    fun syncFromExternal(value: String) {
        if (buffer.text != value) buffer.text = value
    }
}
