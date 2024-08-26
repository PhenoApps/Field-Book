package com.fieldbook.tracker.offbeat.traits.formats.contracts

import com.fieldbook.tracker.R
import com.fieldbook.tracker.offbeat.traits.formats.Formats

class UsbCameraFormat : BasePhotoFormat(
    format = Formats.USB_CAMERA,
    defaultLayoutId = R.layout.trait_camera,
    layoutView = null,
    databaseName  = "usb camera",
    nameStringResourceId = R.string.traits_format_usb_camera,
    iconDrawableResourceId = R.drawable.ic_trait_usb,
    stringNameAux = { ctx -> ctx.getString(R.string.trait_name_alt_usb) }
)