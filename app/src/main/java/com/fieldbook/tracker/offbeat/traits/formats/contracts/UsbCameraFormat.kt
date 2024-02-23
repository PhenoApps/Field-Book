package com.fieldbook.tracker.offbeat.traits.formats.contracts

import com.fieldbook.tracker.R
import com.fieldbook.tracker.offbeat.traits.formats.Formats

class UsbCameraFormat : PhotoFormat(
    format = Formats.USB_CAMERA,
    defaultLayoutId = R.layout.trait_usb_camera,
    layoutView = null,
    nameStringResourceId = R.string.traits_format_usb_camera,
    iconDrawableResourceId = R.drawable.ic_trait_usb,
    stringNameAux = null
)