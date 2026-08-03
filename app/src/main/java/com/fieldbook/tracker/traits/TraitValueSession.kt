package com.fieldbook.tracker.traits

import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.views.CollectInputView

/**
 * Persistence + value buffer for a [BaseTraitLayout].
 *
 * Collect uses the activity's observation pipeline; tree node fields use a
 * sidecar callback so node edits never write the tree trait's CollectInputView.
 */
interface TraitValueSession {
    fun currentTrait(): TraitObject
    fun isLocked(): Boolean
    /** Buffered input used by layouts via [BaseTraitLayout.getCollectInputView]. */
    fun inputView(): CollectInputView
    fun commit(trait: TraitObject, value: String)
    fun clear(trait: TraitObject)
}
