/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// If no local.properties exists, copy from the parent plugin project (which Android Studio generates).
val localProps = file("local.properties")
if (!localProps.exists()) {
    val parentProps = file("../local.properties")
    if (parentProps.exists()) {
        parentProps.copyTo(localProps)
    }
}

pluginManagement {
    // -PusePublishedPluginFrom=../build/repo  → resolve the plugin from a pre-published Maven repo (CI, e2e tests).
    // (unset, default)                         → build the plugin from source via includeBuild (local dev).
    val publishedPluginRepo = providers.gradleProperty("usePublishedPluginFrom").orNull
    if (publishedPluginRepo != null) {
        repositories {
            // The oss-licenses plugin MUST come from the local repo, never Google Maven.
            // exclusiveContent ensures Gradle won't silently fall back to an old published version.
            exclusiveContent {
                forRepository { maven { url = uri(file(publishedPluginRepo)) } }
                filter {
                    includeModule("com.google.android.gms", "oss-licenses-plugin")
                    includeModule("com.google.android.gms.oss-licenses-plugin", "com.google.android.gms.oss-licenses-plugin.gradle.plugin")
                }
            }
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    } else {
        // Local dev: includeBuild substitutes the plugin automatically.
        includeBuild("..")
        repositories {
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Allow overriding the 'play-services-oss-licenses' runtime library with a local version.
        // Usage: ./gradlew :app:test -PlibraryRepoPath=/path/to/your/mavenrepo
        val libraryRepo = providers.gradleProperty("libraryRepoPath").orNull
        if (libraryRepo != null) {
            println("Registering libraryRepoPath: $libraryRepo")
            exclusiveContent {
                forRepository { maven { url = uri(libraryRepo) } }
                filter {
                    includeModule("com.google.android.gms", "play-services-oss-licenses")
                }
            }
        }

        google()
        mavenCentral()
    }
}
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

rootProject.name = "OSS Licenses Test App"
include(":app")
