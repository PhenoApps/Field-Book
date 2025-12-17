package com.fieldbook.tracker.traits.formats.parameters

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

class CategoriesParameter : BaseFormatParameter(
    nameStringResourceId = R.string.traits_create_categories_title,
    defaultLayoutId = R.layout.list_item_trait_parameter_categories,
    parameter = Parameters.CATEGORIES,
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

        private fun addCategory(value: String) {
            if (value.isNotEmpty()) {
                val values = ArrayList<String>()
                for (s in catList) {
                    values.add(s.value)
                }
                if (!values.contains(value)) {
                    val scale = BrAPIScaleValidValuesCategories()
                    scale.label = value
                    scale.value = value
                    catList.add(scale)
                    updateCatAdapter()
                }
            }
        }

        private fun updateCatAdapter() {
            val adapter = categoriesRv.adapter as CategoryAdapter
            adapter.submitList(catList)
            adapter.notifyDataSetChanged()
        }

        override fun bind(parameter: BaseFormatParameter, initialTraitObject: TraitObject?) {
            super.bind(parameter, initialTraitObject)

            addBtn.setOnClickListener { v: View ->
                val value = valueEt.text.toString()
                addCategory(value)
                valueEt.text?.clear()
            }
        }

        override fun merge(traitObject: TraitObject) = traitObject.apply {

            val finalCat = valueEt.text.toString()
            if (finalCat.isNotEmpty()) {
                addCategory(finalCat)
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

                    val labels = listOf(
                        *traitObject.categories.split("/".toRegex())
                            .dropLastWhile { it.isEmpty() }.toTypedArray()
                    )
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
}