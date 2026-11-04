/*
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
rootProject.name = "NewPipe"

pluginManagement {
    repositories {
        google()
        maven(url = "https://maven-central.storage-download.googleapis.com/maven2")
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            val id = requested.id.id
            when (id) {
                "com.android.application",
                "com.android.library" -> {
                    useModule("com.android.tools.build:gradle:${requested.version}")
                }
                "com.google.devtools.ksp" -> {
                    useModule("com.google.devtools.ksp:symbol-processing-gradle-plugin:${requested.version}")
                }
                "com.google.dagger.hilt.android" -> {
                    useModule("com.google.dagger:hilt-android-gradle-plugin:${requested.version}")
                }
                "org.sonarqube" -> {
                    useModule("org.sonarsource.scanner.gradle:sonarqube-gradle-plugin:${requested.version}")
                }
                "org.jetbrains.kotlin.android",
                "org.jetbrains.kotlin.jvm",
                "org.jetbrains.kotlin.kapt",
                "org.jetbrains.kotlin.plugin.parcelize" -> {
                    useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
                }
                "org.jetbrains.kotlin.plugin.serialization" -> {
                    useModule("org.jetbrains.kotlin:kotlin-serialization:${requested.version}")
                }
            }
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven(url = "https://maven-central.storage-download.googleapis.com/maven2")
        maven(url = "https://jitpack.io")
        maven(url = "https://repo.clojars.org")
    }
}
include(":app") // androidApp
include(":tournesol")
include(":desktopApp")
include("shared")

// Use the local submodule when present, otherwise fall back to the remote
// dependency declared in libs.versions.toml.
if (file("NewPipeExtractor/settings.gradle.kts").exists() ||
    file("NewPipeExtractor/settings.gradle").exists()
) {
    includeBuild("NewPipeExtractor") {
        dependencySubstitution {
            substitute(module("com.github.TeamNewPipe:NewPipeExtractor"))
                .using(project(":extractor"))
            substitute(module("com.github.venturqx:NPExtractorTournesol"))
                .using(project(":extractor"))
        }
    }
}
