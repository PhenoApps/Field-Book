package com.fieldbook.shared.screens.collect.traits

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fieldbook.shared.database.models.TraitObject
import com.fieldbook.shared.theme.Button
import com.fieldbook.shared.theme.numericButtonDefaults
import com.fieldbook.shared.utilities.BrAPIScaleValidValuesCategories
import com.fieldbook.shared.utilities.CategoryJsonUtil

@Composable
fun CategoricalTrait(
    trait: TraitObject?,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    multi: Boolean = false,
) {
    // TODO Determine the button text according to the user's preference (label or value)
    val labelValPref = "value"
    println("CategoricalTrait: trait=$trait, value=$value, multi=$multi")

    // Parse the trait's category definition. Try JSON first, then fall back to legacy slash-separated format.
    val categories: ArrayList<BrAPIScaleValidValuesCategories> = try {
        CategoryJsonUtil.decodeCategories(trait?.categories ?: "[]")
    } catch (_: Exception) {
        val fallback = trait?.categories?.split("/")?.map {
            BrAPIScaleValidValuesCategories(label = it, value = it)
        } ?: emptyList()
        ArrayList(fallback)
    }

    // Compute the displayed values from the stored value (which may be JSON or legacy raw)
    val displayedValues: List<String> = try {
        val scale = CategoryJsonUtil.decode(value)
        if (scale.isNotEmpty()) {
            if (labelValPref == "value") scale.mapNotNull { it.value } else scale.mapNotNull { it.label }
        } else if (!multi && value.isNotBlank()) {
            // Legacy single value
            listOf(value)
        } else {
            emptyList()
        }
    } catch (_: Exception) {
        if (!multi && value.isNotBlank()) listOf(value) else emptyList()
    }

    // Display buttons in a grid (3 columns per row)
    val columns = 3

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier,
    ) {
        items(categories) { cat ->
            val buttonText = if (labelValPref == "value") cat.value ?: "" else cat.label ?: ""
            val isSelected = displayedValues.contains(buttonText)
            Button(
                onClick = {
                    if (multi) {
                        // Multi-select: add or remove from selection
                        val scale = CategoryJsonUtil.decode(value).toMutableList()
                        val alreadySelected = scale.any { (if (labelValPref == "value") it.value else it.label) == buttonText }
                        if (alreadySelected) {
                            val newScale = scale.filterNot { (if (labelValPref == "value") it.value else it.label) == buttonText }
                            onValueChange(CategoryJsonUtil.encode(ArrayList(newScale)))
                        } else {
                            scale.add(cat)
                            onValueChange(CategoryJsonUtil.encode(ArrayList(scale)))
                        }
                    } else {
                        // Single-select: toggle selection
                        if (isSelected) {
                            onValueChange("")
                        } else {
                            val scale = arrayListOf(cat)
                            onValueChange(CategoryJsonUtil.encode(scale))
                        }
                    }
                },
                selected = isSelected,
                modifier = Modifier.fillMaxWidth().numericButtonDefaults(),
            ) {
                Text(buttonText)
            }
        }
    }
}
