package com.fieldbook.tracker.offbeat.traits.formats.contracts

import com.fieldbook.tracker.R
import com.fieldbook.tracker.offbeat.traits.formats.Formats
import com.fieldbook.tracker.offbeat.traits.formats.TraitFormat
import com.fieldbook.tracker.offbeat.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.NameParameter

class UsbCameraFormat : TraitFormat(
    format = Formats.USB_CAMERA,
    defaultLayoutId = R.layout.trait_usb_camera,
    layoutView = null,
    databaseName  = "usb camera",
    nameStringResourceId = R.string.traits_format_usb_camera,
    iconDrawableResourceId = R.drawable.ic_trait_usb,
    stringNameAux = null,
    NameParameter(),
    DetailsParameter()
)