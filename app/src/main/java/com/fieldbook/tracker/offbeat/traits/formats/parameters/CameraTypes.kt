package com.fieldbook.tracker.offbeat.traits.formats.parameters

import com.fieldbook.tracker.offbeat.traits.formats.Formats

enum class CameraTypes(var format: Formats) {
    DEFAULT(Formats.CAMERA), USB(Formats.USB_CAMERA), GO_PRO(Formats.GO_PRO), CANON(Formats.CANON)
}