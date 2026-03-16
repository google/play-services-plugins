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
    repositories {
        // Check if the licenses plugin has been built in the parent project. If so, use it here
        // (Not using includeBuild as it would cause a circular dependency with the plugin's e2e tests)
        val localRepo = file("../build/repo")
        if (localRepo.exists()) {
            logger.lifecycle("Using the built plugin from parent project.")
            maven { url = uri(localRepo) }
        } else {
            logger.lifecycle("Fetching OSS Licences plugin from gMaven. To use the plugin built" +
            "from the parent project, run cd .. && /gradlew publish")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
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

        providers.gradleProperty("libraryRepoPath").orNull?.let {
            println("Registering libraryRepoPath: $it")
            maven { url = uri(it) }
        }

        google()
        mavenCentral()
    }
}
rootProject.name = "OSS Licenses Test App"
include(":app")
