package com.fieldbook.tracker.offbeat.traits.formats.parameters

import com.fieldbook.tracker.offbeat.traits.formats.Formats

enum class CameraTypes(var format: Formats) {
    DEFAULT(Formats.CAMERA), CANON(Formats.CANON)
}