package com.fieldbook.shared.screens.collect.traits

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.ic_event_black_24dp
import com.fieldbook.shared.generated.resources.ic_plus
import com.fieldbook.shared.generated.resources.minus
import com.fieldbook.shared.generated.resources.trait_date_save
import com.fieldbook.shared.utilities.dateFormatMonthDay
import com.fieldbook.shared.utilities.epochMillisToLocalDate
import com.fieldbook.shared.utilities.localDateToEpochMillis
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTrait(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    fun nowDate(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    fun parseDate(value: String): LocalDate? {
        if (value == "NA") return null
        if (value.isEmpty()) return nowDate()

        return try {
            LocalDate.parse(value)
        } catch (_: Exception) {
            return nowDate()
        }
    }

    val initialDate = remember(value) { parseDate(value) }
    var localDate by remember(initialDate) { mutableStateOf(initialDate) }
    var showDatePicker by remember { mutableStateOf(false) }


    fun saveFormat(date: LocalDate): String = "${date.year.toString().padStart(4, '0')}-${
        date.monthNumber.toString().padStart(2, '0')
    }-${date.dayOfMonth.toString().padStart(2, '0')}"

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(
                onClick = { localDate = localDate?.plus(-1, DateTimeUnit.DAY) },
                enabled = localDate != null
            ) {
                Icon(
                    painter = painterResource(
                        Res.drawable.minus
                    ),
                    contentDescription = "Previous day"
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = localDate?.let { dateFormatMonthDay(it) } ?: "NA",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.width(16.dp))
            FilledIconButton(
                onClick = { localDate = localDate?.plus(1, DateTimeUnit.DAY) },
                enabled = localDate != null
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_plus),
                    contentDescription = "Next day"
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(
                onClick = { showDatePicker = true },
                enabled = localDate != null
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_event_black_24dp),
                    contentDescription = "Pick date"
                )
            }
            Spacer(Modifier.width(32.dp))
            FilledIconButton(
                onClick = {
                    localDate?.let { onValueChange(saveFormat(it)) }
                },
                enabled = localDate != null
            ) {
                Icon(
                    painter = painterResource(Res.drawable.trait_date_save),
                    contentDescription = "Save date"
                )
            }
        }
        if (localDate == null) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                localDate = nowDate()
            }) { Text("Clear NA") }
        }
    }

    if (showDatePicker && localDate != null) {
        val safeDate = localDate // avoid smart cast error
        val initialMillis = localDateToEpochMillis(safeDate!!)
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                Button(onClick = {
                    val selectedMillis = datePickerState.selectedDateMillis
                    if (selectedMillis != null) {
                        localDate = epochMillisToLocalDate(selectedMillis)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                Button(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
