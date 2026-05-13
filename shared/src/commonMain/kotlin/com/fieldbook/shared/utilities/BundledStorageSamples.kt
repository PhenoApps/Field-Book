package com.fieldbook.shared.utilities

import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.dir_field_import
import com.fieldbook.shared.generated.resources.dir_resources
import com.fieldbook.shared.generated.resources.dir_trait
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.StringResource

private data class BundledStorageSample(
    val bundledPath: String,
    val destinationDirectory: StringResource,
    val destinationFileName: String,
)

private val bundledStorageSamples = listOf(
    BundledStorageSample(
        bundledPath = "files/field_import/field_sample.csv",
        destinationDirectory = Res.string.dir_field_import,
        destinationFileName = "field_sample.csv",
    ),
    BundledStorageSample(
        bundledPath = "files/field_import/field_sample2.csv",
        destinationDirectory = Res.string.dir_field_import,
        destinationFileName = "field_sample2.csv",
    ),
    BundledStorageSample(
        bundledPath = "files/field_import/field_sample3.csv",
        destinationDirectory = Res.string.dir_field_import,
        destinationFileName = "field_sample3.csv",
    ),
    BundledStorageSample(
        bundledPath = "files/field_import/rtk_sample.csv",
        destinationDirectory = Res.string.dir_field_import,
        destinationFileName = "rtk_sample.csv",
    ),
    BundledStorageSample(
        bundledPath = "files/field_import/training_sample.csv",
        destinationDirectory = Res.string.dir_field_import,
        destinationFileName = "training_sample.csv",
    ),
    BundledStorageSample(
        bundledPath = "files/trait/trait_sample.trt",
        destinationDirectory = Res.string.dir_trait,
        destinationFileName = "trait_sample.trt",
    ),
    BundledStorageSample(
        bundledPath = "files/trait/severity.txt",
        destinationDirectory = Res.string.dir_trait,
        destinationFileName = "severity.txt",
    ),
    BundledStorageSample(
        bundledPath = "files/resources/feekes_sample.jpg",
        destinationDirectory = Res.string.dir_resources,
        destinationFileName = "feekes_sample.jpg",
    ),
    BundledStorageSample(
        bundledPath = "files/resources/stem_rust_sample.jpg",
        destinationDirectory = Res.string.dir_resources,
        destinationFileName = "stem_rust_sample.jpg",
    ),
)

@OptIn(ExperimentalResourceApi::class)
suspend fun seedBundledStorageSamples(): Int {
    var copiedCount = 0

    bundledStorageSamples.forEach { sample ->
        val directory = getDirectory(sample.destinationDirectory) ?: return@forEach
        val existing = directory.findFile(sample.destinationFileName)
        if (existing?.exists() == true) {
            return@forEach
        }

        val bytes = Res.readBytes(sample.bundledPath)
        val created = directory.createFile("application/octet-stream", sample.destinationFileName)
            ?: return@forEach
        created.writeBytes(bytes)
        copiedCount++
    }

    return copiedCount
}
