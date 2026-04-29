package com.fieldbook.tracker.traits.formats

import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.formats.parameters.BaseFormatParameter
import com.fieldbook.tracker.traits.formats.parameters.CropImageParameter
import com.fieldbook.tracker.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.traits.formats.parameters.NameParameter
import com.fieldbook.tracker.traits.formats.parameters.SaveImageParameter

class GoProFormat(
    override vararg val parameters: BaseFormatParameter = arrayOf(
        NameParameter(),
        DetailsParameter(),
        CropImageParameter(),
        SaveImageParameter()
    )
) : BasePhotoFormat(
    format = Formats.GO_PRO,
    nameStringResourceId = R.string.traits_format_go_pro_camera,
    databaseName = "gopro",
    iconDrawableResourceId = R.drawable.ic_trait_gopro,
)