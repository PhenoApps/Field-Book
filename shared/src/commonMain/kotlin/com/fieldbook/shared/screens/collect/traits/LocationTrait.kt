package com.fieldbook.shared.screens.collect.traits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.theme.FilledIconButton
import dev.jordond.compass.Location
import dev.jordond.compass.Priority
import dev.jordond.compass.geolocation.Geolocator
import kotlinx.coroutines.launch

private const val GNSS_FIX_INTERNAL_GPS = "GPS"

@Composable
fun LocationTrait(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LocationCaptureTrait(
        value = value,
        onLocationCaptured = { location ->
            onValueChange(location.toLegacyLocationValue())
        },
        emptyLabel = "No location captured",
        actionLabel = "Save location",
        modifier = modifier,
    )
}

@Composable
fun GnssTrait(
    value: String,
    onValueChange: (String) -> Unit,
    onGeoCoordinatesChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LocationCaptureTrait(
        value = value,
        onLocationCaptured = { location ->
            val latitude = location.coordinates.latitude.toString()
            val longitude = location.coordinates.longitude.toString()
            onValueChange("$latitude; $longitude; $GNSS_FIX_INTERNAL_GPS")
            onGeoCoordinatesChange(location.toGeoJson(GNSS_FIX_INTERNAL_GPS))
        },
        emptyLabel = "No GNSS location captured",
        actionLabel = "Use internal GPS",
        modifier = modifier,
    )
}

@Composable
private fun LocationCaptureTrait(
    value: String,
    onLocationCaptured: (Location) -> Unit,
    emptyLabel: String,
    actionLabel: String,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val geolocator = remember { Geolocator() }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = value.ifBlank { emptyLabel },
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        errorMessage?.let { message ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(
                onClick = {
                    coroutineScope.launch {
                        loading = true
                        errorMessage = null
                        val location = runCatching {
                            geolocator.current(Priority.HighAccuracy).getOrNull()
                        }.getOrNull()
                        loading = false

                        if (location == null) {
                            errorMessage = "Location unavailable. Check location services and permissions."
                        } else {
                            onLocationCaptured(location)
                        }
                    }
                },
                enabled = !loading,
                shape = CircleShape,
                modifier = Modifier.size(96.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 3.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = actionLabel,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
        }

    }
}

private fun Location.toLegacyLocationValue(): String {
    val longitude = coordinates.longitude.toString().truncateDecimalString(8)
    val latitude = coordinates.latitude.toString().truncateDecimalString(8)
    return "$longitude; $latitude"
}

private fun Location.toGeoJson(fix: String): String {
    val latitude = coordinates.latitude.toString()
    val longitude = coordinates.longitude.toString()
    return """{"type":"Feature","geometry":{"type":"Point","coordinates":["$longitude","$latitude"]},"properties":{"fix":"$fix"}}"""
}

private fun String.truncateDecimalString(digits: Int): String {
    var count = 0
    var found = false
    val truncated = StringBuilder()

    for (char in this) {
        if (found) {
            count += 1
            if (count == digits) break
        }

        if (char == '.') {
            found = true
        }

        truncated.append(char)
    }

    return truncated.toString()
}
