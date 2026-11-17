package com.fieldbook.tracker.traits.formats.parameters

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.repository.TraitRepository
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.ValidationResult
import com.google.android.material.textfield.TextInputLayout
import java.util.UUID
import javax.inject.Inject

/**
 * This is the supertype class for all Format Parameters.
 * Format Parameters are classes that hold UI and validation logic for creating concrete Formats.
 * Each parameter must have a view holder defined, which is used in the recycler view pattern within
 * the new trait dialog.
 *
 * @param uniqueId
 * @param defaultLayoutId   reference to the view to be inflated
 * @param viewType          ViewTypes are one-to-one with the Parameters enum ordinals, and are used during the
 *                          createViewHolder RV pattern
 */
open class BaseFormatParameter @Inject constructor(
    private val uniqueId: String = UUID.randomUUID().toString(),
    open val nameStringResourceId: Int,
    open val defaultLayoutId: Int,
    open val parameter: Parameters,

    ) {
    /**
     * All parameter names are displayed above the UI as a title.
     */
    fun getName(ctx: Context) = ctx.getString(nameStringResourceId)

    /**
     * The subtype must implement and return a ViewHolder via inflation or programmatically.
     */
    open fun createViewHolder(parent: ViewGroup): ViewHolder? = null

    /**
     * This is a supertype ViewHolder for all parameters which automatically handles titles.
     */
    abstract class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        protected var initialTraitObject: TraitObject? = null

        private val titleTextView =
            itemView.findViewById<TextView>(R.id.list_item_trait_parameter_title)

        val textInputLayout: TextInputLayout =
            itemView.findViewById(R.id.list_item_trait_parameter_til)

        /**
         * The bind function sets the title and calls the subtype load.
         * Some subtypes must override this to do extra UI callbacks.
         */
        open fun bind(parameter: BaseFormatParameter, initialTraitObject: TraitObject? = null) {

            titleTextView.text = parameter.getName(itemView.context)

            load(initialTraitObject)
        }

        /**
         * The merge function merges the UI into the given trait object and returns it.
         */
        abstract fun merge(traitObject: TraitObject): TraitObject


        /**
         * The load function takes a trait object and transfers the pertinent fields
         * into the UI.
         */
        abstract fun load(traitObject: TraitObject?): Boolean

        /**
         * Validation checks the format parameter UI which is specific to the format.
         * The validation result is returned which optionally gives an error message.
         */
        abstract fun validate(
            traitRepo: TraitRepository,
            initialTraitObject: TraitObject? = null
        ): ValidationResult
    }

    override fun equals(other: Any?): Boolean {
        return uniqueId == (other as BaseFormatParameter).uniqueId
    }

    override fun hashCode(): Int {
        return uniqueId.hashCode()
    }
}