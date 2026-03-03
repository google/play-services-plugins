/*
 * Copyright 2025-2026 Google LLC
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

plugins {
    id("groovy")
    id("java-gradle-plugin")
    id("org.jetbrains.kotlin.jvm") version "2.3.10"
    id("com.gradle.plugin-publish") version "1.3.1"
}

group = "com.google.android.gms"
version = "0.11.0"

repositories {
    google()
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

gradlePlugin {
    plugins {
        create("ossLicensesPlugin") {
            id = "com.google.android.gms.oss-licenses-plugin"
            implementationClass = "com.google.android.gms.oss.licenses.plugin.OssLicensesPlugin"
        }
    }
}

dependencies {
    compileOnly("com.android.tools.build:gradle:9.0.1")
    compileOnly("com.android.tools.build:gradle-api:9.0.1")
    implementation(gradleApi())
    implementation(localGroovy())
    implementation("com.google.protobuf:protobuf-java:4.34.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:4.1.0")
    testImplementation("com.google.guava:guava:33.4.0-jre")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("com.google.code.gson:gson:2.12.1")
    testImplementation("com.android.tools.build:gradle:9.0.1") {
        because("Needed for DependencyTaskTest.")
    }
}

val repo: Provider<Directory> = layout.buildDirectory.dir("repo")
tasks.withType<Test>().configureEach {
    val localRepo = repo
    // Make sure that build/repo is created and that it is used as input for the test task.
    // Replace this with something less ugly if https://github.com/gradle/gradle/issues/34870 is fixed
    dependsOn("publish")
    inputs.files(
        localRepo.map {
            // Exclude maven-metadata.xml as they contain timestamps but have no effect on the test outcomes
            it.asFileTree.matching { exclude("**/maven-metadata.xml*") }
        }
    ).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("repo")

    val localVersion = project.version.toString()
    systemProperties["plugin_version"] = localVersion // value used by EndToEndTest.kt
    doFirst {
        // Inside doFirst to make sure that absolute path is not considered to be input to the task
        systemProperties["repo_path"] = localRepo.get().asFile.absolutePath // value used by EndToEndTest.kt
    }
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1
}

publishing {
    repositories {
        maven {
          url = uri(repo)
        }
    }
    publications {
        create<MavenPublication>("pluginMaven") {
            artifactId = "oss-licenses-plugin"
        }
    }
    publications.withType<MavenPublication>().configureEach {
        pom {
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
        }
    }
}
