package com.fieldbook.tracker.traits

import android.content.Context
import com.fieldbook.tracker.traits.formats.Formats

/**
 * Creates a fresh [BaseTraitLayout] controller for a format (never the Collect
 * [LayoutCollections] singleton). Used by tree node fields.
 *
 * Unknown formats fail closed ([create] → null; [isNodeHostable] → false).
 * Collect's [LayoutCollections] may still fall back to text — that is separate.
 *
 * Hostable in-node (AndroidView + [NodeTraitValueSession]): text/numeric/angle/
 * barcode/boolean/categorical/counter/date/disease rating/label print/location/
 * percent/stop_watch (+ seconds)/audio/GNSS/scale/spectral family.
 *
 * Residual blocked (Collect-camera / device-API bound — no soft-host as text):
 * video, USB camera, GoPro, Canon, camera/photo (photo uses [PhotoChromeHost]
 * instead), tree architecture / summary topology shells.
 */
object TraitLayoutFactory {

    private val unsupportedNodeFormats = setOf(
        Formats.VIDEO.getDatabaseName(),
        Formats.TREE_ARCHITECTURE.getDatabaseName(),
        Formats.TREE_SUMMARY.getDatabaseName(),
        Formats.CAMERA.getDatabaseName(),
        Formats.BASE_PHOTO.getDatabaseName(),
        Formats.USB_CAMERA.getDatabaseName(),
        Formats.GO_PRO.getDatabaseName(),
        Formats.CANON.getDatabaseName(),
        "photo",
    ).map { it.lowercase() }.toSet()

    /**
     * True only for formats with an explicit [create] host arm (and not blocked).
     * Unknown keys are not hostable — no soft Text fallback.
     */
    @JvmStatic
    fun isNodeHostable(format: String): Boolean {
        val key = format.lowercase()
        if (key in unsupportedNodeFormats) return false
        return when {
            key == "text" -> true
            key == "numeric" -> true
            key == "angle" -> true
            key == "barcode" -> true
            key == "boolean" -> true
            key == "categorical" || key == "multicat" -> true
            key == "counter" -> true
            key == "date" -> true
            key == "disease rating" -> true
            key == "zebra label print" || key.contains("label") -> true
            key == "location" -> true
            key == "percent" -> true
            key == "stop_watch" || key == "stopwatch" || key == "seconds" -> true
            key == "qualitative" -> true
            key == "audio" -> true
            key == "gnss" -> true
            key == "scale" -> true
            key == "spectral" || key == "nix" || key == "inno_spectra" || key == "green_seeker" -> true
            else -> false
        }
    }

    @JvmStatic
    fun create(format: String, context: Context): BaseTraitLayout? {
        val key = format.lowercase()
        if (key in unsupportedNodeFormats) return null
        return when {
            key == "text" -> TextTraitLayout(context)
            key == "numeric" -> NumericTraitLayout(context)
            key == "angle" -> AngleTraitLayout(context)
            key == "barcode" -> BarcodeTraitLayout(context)
            key == "boolean" -> BooleanTraitLayout(context)
            key == "categorical" || key == "multicat" -> CategoricalTraitLayout(context)
            key == "counter" -> CounterTraitLayout(context)
            key == "date" -> DateTraitLayout(context)
            key == "disease rating" -> DiseaseRatingTraitLayout(context)
            key == "zebra label print" || key.contains("label") -> LabelPrintTraitLayout(context)
            key == "location" -> LocationTraitLayout(context)
            key == "percent" -> PercentTraitLayout(context)
            key == "stop_watch" || key == "stopwatch" || key == "seconds" -> StopWatchTraitLayout(context)
            key == "qualitative" -> CategoricalTraitLayout(context)
            key == "audio" -> AudioTraitLayout(context)
            key == "gnss" -> GNSSTraitLayout(context)
            key == "scale" -> ScaleTraitLayout(context)
            // Nix / Inno / GreenSeeker property-init SpectralSaver(database) requires
            // CollectController context; otherwise host base spectral chrome.
            key == "spectral" -> SpectralTraitLayout(context)
            key == "nix" -> spectralOrBase(context) { NixTraitLayout(it) }
            key == "inno_spectra" -> spectralOrBase(context) { InnoSpectraTraitLayout(it) }
            key == "green_seeker" -> spectralOrBase(context) { GreenSeekerTraitLayout(it) }
            else -> null
        }
    }

    /**
     * Specialized spectral layouts touch [BaseTraitLayout.getDatabase] in field initializers,
     * which requires a [com.fieldbook.tracker.interfaces.CollectController] context.
     * Fall back to [SpectralTraitLayout] chrome when constructed outside Collect.
     */
    private fun spectralOrBase(
        context: Context,
        specialized: (Context) -> BaseTraitLayout,
    ): BaseTraitLayout =
        if (context is com.fieldbook.tracker.interfaces.CollectController) {
            specialized(context)
        } else {
            runCatching { specialized(context) }.getOrElse { SpectralTraitLayout(context) }
        }
}
