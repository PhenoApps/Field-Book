package com.fieldbook.shared.screens.collect.traits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.dir_trait
import com.fieldbook.shared.generated.resources.trait_error_disease_severity
import com.fieldbook.shared.theme.Button
import com.fieldbook.shared.utilities.getDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource

private val fallbackRustCodes = listOf(
    "0", "5", "10", "15", "20", "25", "30", "35", "40", "45", "50",
    "55", "60", "65", "70", "75", "80", "85", "90", "95", "100"
)
private val rustNumericButtonWidth = 62.dp
private val rustLetterButtonWidth = 86.dp
private val rustButtonHeight = 52.dp
private val rustNumericTextStyle = TextStyle(fontSize = 13.sp)
private val rustLetterTextStyle = TextStyle(fontSize = 16.sp)
private val rustButtonContentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)

@OptIn(ExperimentalResourceApi::class)
private suspend fun loadRustCodes(): List<String> = withContext(Dispatchers.Default) {
    val fromStorage = runCatching {
        getDirectory(Res.string.dir_trait)
            ?.findFile("severity.txt")
            ?.takeIf { it.exists() }
            ?.readBytes()
            ?.decodeToString()
            ?.split(Regex("\\s+"))
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.take(21)
            ?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    fromStorage
        ?: runCatching {
            Res.readBytes("files/trait/severity.txt")
                .decodeToString()
                .split(Regex("\\s+"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .take(21)
                .takeIf { it.isNotEmpty() }
        }.getOrNull()
        ?: fallbackRustCodes
}

private fun appendDiseaseRatingValue(currentValue: String, pressedValue: String): String? {
    if (currentValue.isNotEmpty() &&
        pressedValue != "/" &&
        !currentValue.endsWith("/")
    ) {
        val lastChar = currentValue.last()
        if (!lastChar.isLetter()) {
            return currentValue + ":" + pressedValue
        }
    }

    return currentValue + pressedValue
}

@Composable
fun DiseaseRatingTrait(
    value: String,
    onValueChange: (String) -> Unit,
    onValidationError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val severityAlreadyStoredText = stringResource(Res.string.trait_error_disease_severity)
    val rustCodes by produceState(initialValue = fallbackRustCodes) {
        this.value = loadRustCodes()
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        rustCodes.chunked(6).forEach { rowCodes ->
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.Start)
                ) {
                    rowCodes.forEach { code ->
                        Button(
                            onClick = {
                                if (value.contains(Regex("\\d")) &&
                                    code.any { it.isDigit() } &&
                                    !value.contains("/")
                                ) {
                                    onValidationError(severityAlreadyStoredText)
                                } else {
                                    appendDiseaseRatingValue(value, code)?.let(onValueChange)
                                }
                            },
                            selected = false,
                            modifier = Modifier
                                .width(rustNumericButtonWidth)
                                .height(rustButtonHeight),
                            contentPadding = rustButtonContentPadding,
                        ) {
                            Text(
                                text = code,
                                color = Color.Black,
                                style = rustNumericTextStyle,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip
                            )
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.Start)
            ) {
                listOf("R", "M", "S", "/").forEach { code ->
                    Button(
                        onClick = {
                            appendDiseaseRatingValue(value, code)?.let(onValueChange)
                        },
                        selected = false,
                        modifier = Modifier
                            .width(rustLetterButtonWidth)
                            .height(rustButtonHeight),
                        contentPadding = rustButtonContentPadding,
                    ) {
                        Text(
                            text = code,
                            color = Color.Black,
                            style = rustLetterTextStyle,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            }
        }
    }
}
