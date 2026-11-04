/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
import com.android.build.api.dsl.LibraryExtension
import dev.detekt.gradle.Detekt

plugins {
    alias(libs.plugins.android.plain.library)
    alias(libs.plugins.detekt)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // TODO: Drop annotation default target when it is stable
        freeCompilerArgs.addAll(
            "-Xannotation-default-target=param-property"
        )
    }
}

configure<LibraryExtension> {
    compileSdk {
        version = release(NEWPIPE_VERSION_SDK_COMPILE_MAJOR) {
            minorApiLevel = NEWPIPE_VERSION_SDK_COMPILE_MINOR
        }
    }
    namespace = "org.schabi.newpipe.tournesol"

    defaultConfig {
        minSdk {
            version = release(NEWPIPE_VERSION_SDK_MIN)
        }
    }

    compileOptions {
        // TournesolAuthManager uses java.time, which needs desugaring below API 26
        isCoreLibraryDesugaringEnabled = true
        encoding = "utf-8"
    }
}

detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    basePath = rootProject.layout.projectDirectory
    ignoreFailures = true
}

tasks.withType<Detekt>().configureEach {
    reports {
        checkstyle.required = false
        html.required = false
        markdown.required = false
        sarif.required = true
    }
}

dependencies {
    // Desugaring
    coreLibraryDesugaring(libs.android.desugar)

    // NewPipe libraries
    implementation(libs.newpipe.nanojson)
    implementation(libs.newpipe.extractor)

    detektPlugins(libs.nlopez.compose.rules.detekt)

    /** AndroidX **/
    implementation(libs.androidx.preference)
    implementation(libs.androidx.security.crypto)

    // HTTP client
    implementation(libs.squareup.okhttp)
    implementation(libs.appauth)

    // Reactive extensions for Java VM
    implementation(libs.reactivex.rxjava)

    /** Testing **/
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
}
