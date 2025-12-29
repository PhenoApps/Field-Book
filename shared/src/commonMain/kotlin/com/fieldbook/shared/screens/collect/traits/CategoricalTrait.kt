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
    modifier: Modifier = Modifier
) {
    // TODO Determine the button text according to the user's preference (label or value)
    val labelValPref = "value"
    println("CategoricalTrait: trait=$trait, value=$value")

    // Parse the trait's category definition. Try JSON first, then fall back to legacy slash-separated format.
    val categories: ArrayList<BrAPIScaleValidValuesCategories> = try {
        CategoryJsonUtil.decodeCategories(trait?.categories ?: "[]")
    } catch (_: Exception) {
        val fallback = trait?.categories?.split("/")?.map {
            BrAPIScaleValidValuesCategories(label = it, value = it)
        } ?: emptyList()
        ArrayList(fallback)
    }

    // Compute the displayed text from the stored value (which may be JSON or legacy raw)
    val displayedValue: String = try {
        val scale = CategoryJsonUtil.decode(value)
        if (scale.isNotEmpty()) {
            if (labelValPref == "value") scale[0].value ?: "" else scale[0].label ?: ""
        } else ""
    } catch (_: Exception) {
        // If decode unexpectedly fails, fall back to the raw stored string
        value
    }

    // Display buttons in a grid (3 columns per row)
    val columns = 3

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier,
    ) {
        items(categories) { cat ->
            val buttonText = if (labelValPref == "value") cat.value ?: "" else cat.label ?: ""
            Button(
                onClick = {
                    // Toggle selection: if already selected, clear (emit encoded empty list), otherwise emit encoded single-item list
                    if (displayedValue == buttonText) {
                        onValueChange("")
                    } else {
                        val scale = ArrayList<BrAPIScaleValidValuesCategories>()
                        scale.add(cat)
                        onValueChange(CategoryJsonUtil.encode(scale))
                    }
                },
                selected = displayedValue == buttonText,
                modifier = Modifier.fillMaxWidth().numericButtonDefaults(),
            ) {
                Text(buttonText)
            }
        }
    }
}
