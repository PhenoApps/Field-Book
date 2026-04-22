import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.compose)
    alias(libs.plugins.app.cash.sqldelight)
    alias(libs.plugins.kotlinx.serialization)
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
                implementation(libs.filekit.core)
                implementation(libs.filekit.compose)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlin.csv)
                implementation(libs.ktor.client)
                implementation(libs.ktor.client.cio)
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
        dependsOn(unzipSampleDb)
        dependsOn(copyTraitAssets)
    }
