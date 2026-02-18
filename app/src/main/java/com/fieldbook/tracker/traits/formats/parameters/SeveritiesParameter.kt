package com.fieldbook.tracker.traits.formats.parameters

import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageButton
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.CategoryAdapter
import com.fieldbook.tracker.database.repository.TraitRepository
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.ValidationResult
import com.fieldbook.tracker.utilities.CategoryJsonUtil.Companion.decodeCategories
import com.fieldbook.tracker.utilities.CategoryJsonUtil.Companion.encode
import com.google.android.material.textfield.TextInputEditText
import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories

class SeveritiesParameter : BaseFormatParameter(
    nameStringResourceId = R.string.traits_create_severities_title,
    defaultLayoutId = R.layout.list_item_trait_parameter_categories,
    parameter = Parameters.SEVERITIES,
) {
    override fun createViewHolder(
        parent: ViewGroup,
    ): BaseFormatParameter.ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_trait_parameter_categories, parent, false)
        return ViewHolder(v)
    }

    class ViewHolder(itemView: View) : BaseFormatParameter.ViewHolder(itemView),
        CategoryAdapter.CategoryListItemOnClick {

        private var catList: ArrayList<BrAPIScaleValidValuesCategories> = ArrayList()

        val valueEt: TextInputEditText =
            itemView.findViewById(R.id.list_item_trait_parameter_categories_add_value_et)
        val addBtn: AppCompatImageButton =
            itemView.findViewById(R.id.list_item_trait_parameter_categories_add_btn)
        val categoriesRv: RecyclerView =
            itemView.findViewById(R.id.list_item_trait_parameter_categories_rv)

        private fun setupCategoriesRecyclerView() {
            categoriesRv.adapter = CategoryAdapter(this)
        }

        private fun addSeverity(value: String) {
            if (value.isNotEmpty()) {
                // validate numeric
                val numericValue = value.toDoubleOrNull() ?: return

                val existing = catList.map { it.value }
                if (!existing.contains(value)) {
                    val scale = BrAPIScaleValidValuesCategories()
                    scale.label = value
                    scale.value = value
                    catList.add(scale)
                    sortByMagnitude()
                    updateCatAdapter()
                }
            }
        }

        private fun sortByMagnitude() {
            catList.sortWith(compareBy { it.value.toDoubleOrNull() ?: Double.MAX_VALUE })
        }

        private fun updateCatAdapter() {
            val adapter = categoriesRv.adapter as CategoryAdapter
            adapter.submitList(ArrayList(catList))
            adapter.notifyDataSetChanged()
        }

        override fun bind(parameter: BaseFormatParameter, initialTraitObject: TraitObject?) {
            super.bind(parameter, initialTraitObject)

            // enforce numeric input
            valueEt.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED

            addBtn.setOnClickListener {
                val value = valueEt.text.toString()
                addSeverity(value)
                valueEt.text?.clear()
            }
        }

        override fun merge(traitObject: TraitObject) = traitObject.apply {
            val finalVal = valueEt.text.toString()
            if (finalVal.isNotEmpty()) {
                addSeverity(finalVal)
                valueEt.setText("")
            }

            try {
                categories = encode(catList)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun loadInitialTraitObject(traitObject: TraitObject): Boolean {
            try {
                try {
                    val json = decodeCategories(traitObject.categories)
                    if (json.isNotEmpty()) {
                        catList.addAll(json)
                    }
                } catch (_: Exception) {
                    // try slash-delimited fallback
                    val labels = traitObject.categories.split("/")
                        .filter { it.isNotEmpty() }
                    for (l in labels) {
                        val scale = BrAPIScaleValidValuesCategories()
                        scale.label = l
                        scale.value = l
                        catList.add(scale)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
            return true
        }

        override fun load(traitObject: TraitObject?): Boolean {
            setupCategoriesRecyclerView()
            catList.clear()

            traitObject?.let { t ->
                if (!loadInitialTraitObject(t)) {
                    return false
                }
            }

            // if no categories loaded, use default severity values
            if (catList.isEmpty()) {
                val defaults = listOf(
                    "0", "5", "10", "15", "20", "25", "30", "35", "40", "45",
                    "50", "55", "60", "65", "70", "75", "80", "85", "90", "95", "100"
                )
                for (v in defaults) {
                    val scale = BrAPIScaleValidValuesCategories()
                    scale.label = v
                    scale.value = v
                    catList.add(scale)
                }
            }

            sortByMagnitude()
            updateCatAdapter()
            return true
        }

        override fun validate(
            traitRepo: TraitRepository,
            initialTraitObject: TraitObject?
        ) = ValidationResult()

        override fun onCategoryClick(label: String) {
            var scale: BrAPIScaleValidValuesCategories? = null
            for (s in catList) {
                if (s.label == label) {
                    scale = s
                    break
                }
            }
            if (scale != null) {
                catList.remove(scale)
                updateCatAdapter()
            }
        }
    }

    companion object {
        val DEFAULT_SEVERITIES = listOf(
            "0", "5", "10", "15", "20", "25", "30", "35", "40", "45",
            "50", "55", "60", "65", "70", "75", "80", "85", "90", "95", "100"
        )
    }
}
