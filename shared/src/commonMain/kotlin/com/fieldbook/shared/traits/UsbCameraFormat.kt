package com.fieldbook.shared.traits

import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.ic_trait_usb
import com.fieldbook.shared.generated.resources.traits_format_usb_camera

class UsbCameraFormat : TraitFormat(
    format = Formats.USB_CAMERA,
    nameStringResource = Res.string.traits_format_usb_camera,
    iconDrawableResource = Res.drawable.ic_trait_usb,
)

