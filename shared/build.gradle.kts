import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import java.net.URI

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.compose)
    alias(libs.plugins.app.cash.sqldelight)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.openapi.generator)
}

data class OpenApiSpec(
    val name: String,
    val inputSpec: String,
    val versionPackage: String,
    val modulePackage: String,
) {
    val packageName = "com.fieldbook.shared.generated.brapi.$versionPackage.$modulePackage"
}

fun brapiV2Spec(
    name: String,
    inputSpec: String,
    modulePackage: String,
) = OpenApiSpec(
    name = name,
    inputSpec = inputSpec,
    versionPackage = "v2",
    modulePackage = modulePackage,
)

fun brapiV1Spec(
    name: String,
    inputSpec: String,
    modulePackage: String,
) = OpenApiSpec(
    name = name,
    inputSpec = inputSpec,
    versionPackage = "v1",
    modulePackage = modulePackage,
)

val brapiOpenApiSpecs = listOf(
    brapiV1Spec(
        name = "brapi",
        inputSpec = "https://api.swaggerhub.com/apis/PlantBreedingAPI/BrAPI/1.3/swagger.json",
        modulePackage = "brapi",
    ),
    brapiV2Spec(
        name = "brapiCore",
        inputSpec = "https://api.swaggerhub.com/apis/PlantBreedingAPI/BrAPI-Core/2.1/swagger.json",
        modulePackage = "core",
    ),
    brapiV2Spec(
        name = "brapiPhenotyping",
        inputSpec = "https://api.swaggerhub.com/apis/PlantBreedingAPI/BrAPI-Phenotyping/2.1/swagger.json",
        modulePackage = "phenotyping",
    ),
)

val generatedOpenApiSourceDirs = brapiOpenApiSpecs.associate { spec ->
    spec.name to layout.buildDirectory.dir("generated/openapi/${spec.versionPackage}/${spec.name}/src/commonMain/kotlin")
}

val patchedOpenApiSpecFiles = brapiOpenApiSpecs.associate { spec ->
    spec.name to layout.buildDirectory.file("openapi/specs/${spec.versionPackage}/${spec.name}.patched.json")
}

fun schemaRequiresSerializationWrapper(schema: Map<*, *>): Boolean {
    return schema["type"] == "array" ||
        (schema["type"] == "object" && schema["additionalProperties"] is Map<*, *>)
}

/**
 * Marks top-level array/map request bodies as required before code generation.
 *
 * OpenAPI Generator's Kotlin multiplatform client wraps top-level array/map
 * request bodies in generated serializers. When those request bodies are
 * optional, the generated API method accepts a nullable value but the wrapper
 * constructor expects a non-null value, which does not compile. BrAPI POST/PUT
 * collection bodies are semantically required, so this patches the generated
 * spec before the Kotlin client is generated.
 *
 * Example generated code:
 *  before `RequestWrapper(requestBody: Map<String, X>?)`;
 *  after  `RequestWrapper(requestBody: Map<String, X>)`.
 */
fun patchOptionalWrappedRequestBodies(value: Any?) {
    when (value) {
        is Map<*, *> -> {
            val mutableValue = value as? MutableMap<String, Any?>
            val content = mutableValue?.get("content") as? Map<*, *>
            val hasWrappedRequestBody = content
                ?.values
                ?.filterIsInstance<Map<*, *>>()
                ?.mapNotNull { it["schema"] as? Map<*, *> }
                ?.any(::schemaRequiresSerializationWrapper)
                ?: false

            if (hasWrappedRequestBody && mutableValue?.containsKey("required") == false) {
                mutableValue["required"] = true
            }

            value.values.forEach(::patchOptionalWrappedRequestBodies)
        }

        is Iterable<*> -> value.forEach(::patchOptionalWrappedRequestBodies)
    }
}

/**
 * BrAPI describes `additionalInfo` as arbitrary JSON, but some schemas constrain
 * it to `Map<String, String>`. Real servers return arrays/objects in that map,
 * which breaks generated kotlinx serializers. We do not consume additionalInfo
 * in the shared prototype, so remove it from generated models and let
 * ignoreUnknownKeys skip it in responses.
 */
fun removeAdditionalInfoProperties(value: Any?) {
    when (value) {
        is Map<*, *> -> {
            (value as? MutableMap<String, Any?>)?.let { mutableValue ->
                val properties = mutableValue["properties"] as? MutableMap<String, Any?>
                properties?.remove("additionalInfo")
            }

            value.values.forEach(::removeAdditionalInfoProperties)
        }

        is Iterable<*> -> value.forEach(::removeAdditionalInfoProperties)
    }
}

/**
 * Ontology references are present in several BrAPI responses, but real servers
 * can return null IDs/names or omit them entirely. Keep these fields nullable
 * and optional so generated serializers do not reject otherwise usable records.
 */
fun relaxOntologyReferenceFields(value: Any?) {
    when (value) {
        is Map<*, *> -> {
            (value as? MutableMap<String, Any?>)?.let { mutableValue ->
                val properties = mutableValue["properties"] as? MutableMap<String, Any?>
                listOf("ontologyDbId", "ontologyName").forEach { propertyName ->
                    (properties?.get(propertyName) as? MutableMap<String, Any?>)?.set("nullable", true)
                }

                val required = mutableValue["required"] as? MutableList<Any?>
                required?.removeAll(listOf("ontologyDbId", "ontologyName"))
                if (required?.isEmpty() == true) {
                    mutableValue.remove("required")
                }
            }

            value.values.forEach(::relaxOntologyReferenceFields)
        }

        is Iterable<*> -> value.forEach(::relaxOntologyReferenceFields)
    }
}

/**
 * BrAPI defines observationUnitPosition.entryType as CHECK/TEST/FILLER, but
 * servers may return local labels. Generate entryType as a plain string so any
 * server-provided value can be decoded.
 */
fun relaxObservationUnitEntryTypeEnums(value: Any?) {
    when (value) {
        is Map<*, *> -> {
            val entryType = (value as? MutableMap<String, Any?>)?.get("entryType") as? MutableMap<String, Any?>
            entryType?.remove("enum")

            value.values.forEach(::relaxObservationUnitEntryTypeEnums)
        }

        is Iterable<*> -> value.forEach(::relaxObservationUnitEntryTypeEnums)
    }
}

val patchOpenApiSpecTasks = brapiOpenApiSpecs.map { spec ->
    val patchedSpecFile = patchedOpenApiSpecFiles.getValue(spec.name)

    tasks.register("patch${spec.name.replaceFirstChar { it.uppercase() }}OpenApiSpec") {
        group = "openapi tools"
        description = "Downloads and patches the OpenAPI spec for ${spec.name}."

        inputs.property("inputSpec", spec.inputSpec)
        outputs.file(patchedSpecFile)

        doLast {
            val parsedSpec = JsonSlurper().parse(URI(spec.inputSpec).toURL()) as MutableMap<String, Any?>
            patchOptionalWrappedRequestBodies(parsedSpec)
            removeAdditionalInfoProperties(parsedSpec)
            relaxOntologyReferenceFields(parsedSpec)
            relaxObservationUnitEntryTypeEnums(parsedSpec)

            val outputFile = patchedSpecFile.get().asFile
            outputFile.parentFile.mkdirs()
            outputFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(parsedSpec)))
        }
    }
}

val generatedOpenApiTasks = brapiOpenApiSpecs.map { spec ->
    val generatedDir = layout.buildDirectory.dir("generated/openapi/${spec.versionPackage}/${spec.name}")
    val patchedSpecFile = patchedOpenApiSpecFiles.getValue(spec.name)
    val patchSpecTask = patchOpenApiSpecTasks.single { it.name == "patch${spec.name.replaceFirstChar { char -> char.uppercase() }}OpenApiSpec" }

    tasks.register<GenerateTask>("generate${spec.name.replaceFirstChar { it.uppercase() }}OpenApiClient") {
        group = "openapi tools"
        description = "Generates the Kotlin Multiplatform client for ${spec.name}."
        dependsOn(patchSpecTask)

        generatorName.set("kotlin")
        library.set("multiplatform")
        inputSpec.set(patchedSpecFile)
        outputDir.set(generatedDir.get().asFile.absolutePath)
        packageName.set(spec.packageName)
        apiPackage.set("${spec.packageName}.api")
        modelPackage.set("${spec.packageName}.model")

        doFirst {
            delete(generatedDir)
        }

        configOptions.set(
            mapOf(
                "dateLibrary" to "string",
                "enumPropertyNaming" to "UPPERCASE",
                "generateOneOfAnyOfWrappers" to "true",
                "sourceFolder" to "src/commonMain/kotlin",
            )
        )

        globalProperties.set(
            mapOf(
                "apiDocs" to "false",
                "modelDocs" to "false",
                "apiTests" to "false",
                "modelTests" to "false",
            )
        )
    }
}

kotlin {

    // Target declarations - add or remove as needed below. These define
    // which platforms this KMP module supports.
    // See: https://kotlinlang.org/docs/multiplatform-discover-project.html#targets
    androidLibrary {
        namespace = "com.fieldbook.shared"
        compileSdk = 34
        minSdk = 21

        withHostTestBuilder {
        }

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    // For iOS targets, this is also where you should
    // configure native binary output. For more information, see:
    // https://kotlinlang.org/docs/multiplatform-build-native-binaries.html#build-xcframeworks

    // A step-by-step guide on how to include this library in an XCode
    // project can be found here:
    // https://developer.android.com/kotlin/multiplatform/migrate
    val xcfName = "sharedKit"

    val xcf = XCFramework(xcfName)
    val iosTargets = listOf(iosX64(), iosArm64(), iosSimulatorArm64())

    iosTargets.forEach {
        it.binaries.framework {
            baseName = xcfName
            xcf.add(this)
        }
    }

    // Source set declarations.
    // Declaring a target automatically creates a source set with the same name. By default, the
    // Kotlin Gradle Plugin creates additional source sets that depend on each other, since it is
    // common to share sources between related targets.
    // See: https://kotlinlang.org/docs/multiplatform-hierarchy.html
    sourceSets {
        commonMain {
            brapiOpenApiSpecs.forEach { spec ->
                kotlin.srcDir(generatedOpenApiSourceDirs.getValue(spec.name))
            }

            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation("io.github.kashif-mehmood-km:camerak:0.0.6")
                implementation("io.github.kashif-mehmood-km:qr_scanner_plugin:0.0.6")
                implementation(libs.multiplatform.settings)
                implementation(libs.kotlinx.datetime)
                implementation(libs.lifecycle.viewmodel.compose)
                implementation(libs.okio)
                implementation(libs.permissions)
                implementation(libs.permissions.camera)
                implementation(libs.permissions.compose)
                implementation(libs.permissions.location)
                implementation(libs.permissions.microphone)
                implementation(libs.filekit.core)
                implementation(libs.filekit.compose)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.serialization.csv)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.coil.compose)
                implementation(libs.reorderable)
            }
        }

        commonTest {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test:2.1.10")
            }
        }

        androidMain {
            dependencies {
                // Add Android-specific dependencies here. Note that this source set depends on
                // commonMain by default and will correctly pull the Android artifacts of any KMP
                // dependencies declared in commonMain.
                implementation("app.cash.sqldelight:android-driver:2.1.0")
                implementation(libs.lifecycle.viewmodel.compose)
                implementation(libs.ktor.client.okhttp)
                implementation("com.github.phenoapps:phenolib:v0.9.53")
            }
        }

        getByName("androidDeviceTest") {
            dependencies {
                implementation("androidx.test:runner:1.5.2")
                implementation("androidx.test:core:1.5.0")
                implementation("androidx.test.ext:junit:1.1.5")
            }
        }

        iosMain {
            dependencies {
                // Add iOS-specific dependencies here. This a source set created by Kotlin Gradle
                // Plugin (KGP) that each specific iOS target (e.g., iosX64) depends on as
                // part of KMP’s default source set hierarchy. Note that this source set depends
                // on common by default and will correctly pull the iOS artifacts of any
                // KMP dependencies declared in commonMain.
                implementation(libs.ktor.client.darwin)
                implementation("app.cash.sqldelight:native-driver:2.1.0")
            }
        }
    }

}

compose.resources {
    packageOfResClass = "com.fieldbook.shared.generated.resources"
}

// Package used by Compose resources (must match the one above)
val resPackage = "com.fieldbook.shared.generated.resources"

// ❶ Aggregator: generates Res class + prepares resources for common and iOS
tasks.register("prepareComposeResourcesForXcode") {
    dependsOn(
        "generateComposeResClass",
        "prepareComposeResourcesTaskForCommonMain",
        "prepareComposeResourcesTaskForAppleMain",
        "prepareComposeResourcesTaskForIosMain",
        "prepareComposeResourcesTaskForIosSimulatorArm64Main",
        "prepareComposeResourcesTaskForIosArm64Main"
    )
}

// ❷ Preparation: build the EXACT design that iOS expects in the package
// Result: shared/build/xcode/compose-resources/composeResources/&lt;paquete&gt;/...
tasks.register<Sync>("stageComposeResourcesForXcode") {
    dependsOn("prepareComposeResourcesForXcode")
    from(layout.buildDirectory.dir("generated/compose/resourceGenerator/preparedResources/commonMain/composeResources"))
    into(layout.buildDirectory.dir("xcode/compose-resources/composeResources/$resPackage"))
}

sqldelight {
    databases {
        create("FieldbookDatabase") {
            packageName.set("com.fieldbook.shared.sqldelight")
        }
    }
}

val unzipSampleDb by tasks.registering(Sync::class) {
    val zipFile = file("../app/src/main/assets/database/sample_db.zip")
    val outputDir = layout.projectDirectory.dir("./src/commonMain/composeResources/files")
    from(zipTree(zipFile))
    into(outputDir)
    includeEmptyDirs = false
}

val copyTraitAssets by tasks.registering(Sync::class) {
    from(layout.projectDirectory.dir("../app/src/main/assets/trait"))
    into(layout.projectDirectory.dir("./src/commonMain/composeResources/files/trait"))
    includeEmptyDirs = false
}

// Ensure resource-copy tasks that may consume the generated files depend on this task.
// This avoids the Gradle warning about using a task output without declaring a dependency.
tasks.matching { it.name == "copyNonXmlValueResourcesForCommonMain" }
    .configureEach {
        dependsOn(unzipSampleDb)
        dependsOn(copyTraitAssets)
    }


tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>()
    .configureEach {
        dependsOn(generatedOpenApiTasks)
        dependsOn(unzipSampleDb)
        dependsOn(copyTraitAssets)
    }

tasks.matching { it.name.startsWith("compile") && it.name.contains("Kotlin") }
    .configureEach {
        dependsOn(generatedOpenApiTasks)
    }
