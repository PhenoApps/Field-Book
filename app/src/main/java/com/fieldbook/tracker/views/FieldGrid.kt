package com.fieldbook.tracker.views

import com.fieldbook.tracker.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fieldbook.tracker.utilities.FieldStartCorner
import com.fieldbook.tracker.utilities.FieldStartCorner.Companion.displayText
import com.fieldbook.tracker.utilities.FieldStartCorner.Companion.getAvailableCorners
import com.fieldbook.tracker.utilities.FieldStartCorner.Companion.getCornerAlignment
import kotlin.math.max
import kotlin.math.min

@Composable
fun FieldGrid(
    rows: Int,
    cols: Int,
    modifier: Modifier = Modifier,
    showCornerButtons: Boolean = false,
    selectedCorner: FieldStartCorner? = null,
    onCornerSelected: ((FieldStartCorner) -> Unit)? = null,
    maxSize: Dp = 280.dp
) {
    val cellSize = calculateOptimalGridSize(rows, cols, maxSize)
    val totalWidth = cellSize * cols
    val totalHeight = cellSize * rows
    val extraPadding = if (showCornerButtons) 16.dp else 0.dp

    Box(
        modifier = modifier.size(
            width = totalWidth + extraPadding,
            height = totalHeight + extraPadding
        ),
        contentAlignment = Alignment.Center
    ) {
        GridBackground(
            rows = rows,
            cols = cols,
            cellSize = cellSize
        )

        if (showCornerButtons && onCornerSelected != null) {
            getAvailableCorners(rows, cols).forEach { corner ->
                CornerButton(
                    corner = corner,
                    isSelected = corner == selectedCorner,
                    onClick = { onCornerSelected(corner) },
                    modifier = Modifier.align(getCornerAlignment(corner))
                )
            }
        }
    }
}

@Composable
private fun GridBackground(
    rows: Int,
    cols: Int,
    cellSize: Dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        repeat(rows) { row ->
            Row {
                repeat(cols) { col ->
                    Box(
                        modifier = Modifier
                            .size(cellSize)
                            .background(Color.White)
                            .border(0.5.dp, Color.Gray.copy(alpha = 0.5f))
                    )
                }
            }
        }
    }
}

@Composable
private fun CornerButton(
    corner: FieldStartCorner,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonSize = 40.dp
    val context = LocalContext.current
    val fbPrimary = remember {
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(R.attr.fb_color_primary, typedValue, true)
        Color(typedValue.data)
    }

    Button(
        onClick = onClick,
        colors = if (isSelected) {
            ButtonDefaults.buttonColors(containerColor = fbPrimary,contentColor = Color.White)
        } else {
            ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = fbPrimary)
        },
        modifier = modifier.size(buttonSize).clip(RoundedCornerShape(6.dp)),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = corner.displayText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun calculateOptimalGridSize(rows: Int, cols: Int, maxSize: Dp): Dp {
    // calculate cell size based on available space
    val cellSizeBasedOnWidth = maxSize / cols
    val cellSizeBasedOnHeight = maxSize / rows

    // use the smaller of the two to ensure the grid fits
    val optimalCellSize = min(cellSizeBasedOnWidth.value, cellSizeBasedOnHeight.value)

    // ensure minimum readable size
    val minCellSize = 20f
    val maxCellSize = 50f

    val finalCellSize = max(minCellSize, min(maxCellSize, optimalCellSize)).dp

    return finalCellSize
}