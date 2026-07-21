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

import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

plugins {
    id("groovy")
    id("java-gradle-plugin")
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
    id("com.gradle.plugin-publish") version "2.1.1"
}

group = "com.google.android.gms"
version = "0.13.0"

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
    compileOnly("com.android.tools.build:gradle:9.3.0")
    compileOnly("com.android.tools.build:gradle-api:9.3.0")
    implementation(gradleApi())
    implementation(localGroovy())
    implementation("com.google.protobuf:protobuf-java:4.35.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("com.google.guava:guava:33.6.0-jre")
    testImplementation("com.google.truth:truth:1.4.5")
    testImplementation("com.google.code.gson:gson:2.14.0")
    testImplementation("com.android.tools.build:gradle:9.3.0") {
        because("Needed for DependencyTaskTest.")
    }
}

// AGP/Gradle version matrix — single source of truth for all GradleTestKit tests.
// Each entry maps a test subclass name to its (AGP, Gradle) version pair.
// The versions are injected as system properties so the test files contain no hardcoded versions.
// Keep the keys in sync with the agp-version-key matrices in
// .github/workflows/oss-licenses.yml.
// E2E versions are a subset of the integration versions. Integration tests extend the E2E set
// with older AGP versions to ensure broad backward compatibility.
val e2eVersions = mapOf(
    "AGP813"      to ("8.13.2" to "8.14.1"),       // latest stable 8.x (bumped from 8.12.2)
    "AGP_STABLE"  to ("9.2.1" to "9.6.0"),         // latest stable 9.x using latest stable Gradle (9.6.0)
    "AGP_ALPHA"   to ("9.4.0-alpha01" to "9.6.0"), // latest alpha using latest stable Gradle (no active RC; satisfies >= 9.5.0)
)
val integrationOnlyVersions = mapOf(
    "AGP74" to ("7.4.2" to "7.5.1"), // oldest supported
    "AGP87" to ("8.7.3" to "8.9"),   // mainstream mid-range
)

// Build the full maps with class-name prefixes
val e2eTestVersions = e2eVersions.mapKeys { "EndToEndTest_${it.key}" }
val integrationTestVersions = (e2eVersions + integrationOnlyVersions).mapKeys { "IntegrationTest_${it.key}" }

// Separate source set for GradleTestKit integration tests that run the plugin
// against the AGP/Gradle matrix. Keeps the default 'test' task fast.
val integrationTest by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

configurations[integrationTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[integrationTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    "integrationTestImplementation"(gradleTestKit())
}

val repo: Provider<Directory> = layout.buildDirectory.dir("repo")

// Shared Test-task settings that apply to both unit and integration tests.
tasks.withType<Test>().configureEach {
    minHeapSize = "512m"
    maxHeapSize = "2g"
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Common TestKit setup: both integration and e2e tasks spawn GradleRunner instances
// that need the locally-published plugin, a Java 21 toolchain path for newer AGP, and
// a repo_path pointing at the local publication directory.
fun Test.configureTestKitDefaults() {
    val localRepo = repo
    val javaToolchains = project.extensions.getByType<JavaToolchainService>()
    val java21Home = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    }.map { it.metadata.installationPath.asFile.absolutePath }

    // Make sure that build/repo is created and that it is used as input for the test task.
    // Replace this with something less ugly if https://github.com/gradle/gradle/issues/34870 is fixed
    // Target the specific local-repo publish task, not the umbrella "publish", so that
    // adding other repos (e.g. plugin-portal) doesn't start publishing them during tests.
    dependsOn("publishAllPublicationsToLocalRepository")
    inputs.files(
        localRepo.map {
            // Exclude maven-metadata.xml as they contain timestamps but have no effect on the test outcomes
            it.asFileTree.matching { exclude("**/maven-metadata.xml*") }
        }
    ).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("repo")

    val localVersion = project.version.toString()
    systemProperties["plugin_version"] = localVersion
    // Point TestKit to a directory inside the host Gradle User Home so it can be cached by CI (setup-gradle).
    systemProperties["testkit_path"] = File(System.getProperty("user.home"), ".gradle/testkit").absolutePath
    doFirst {
        // Resolved inside doFirst so contributors without JDK 21 can still run ./gradlew help, tasks, etc.
        // — the toolchain is only required when a Test task actually executes.
        systemProperties["java21_home"] = java21Home.get()
        // Inside doFirst to keep absolute paths out of the task input fingerprint.
        systemProperties["repo_path"] = localRepo.get().asFile.absolutePath
    }
}

val integrationTestTask by tasks.registering(Test::class) {
    description = "Runs GradleTestKit integration tests against the AGP/Gradle version matrix"
    group = "verification"
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath

    configureTestKitDefaults()

    // Inject AGP/Gradle version pairs as system properties for each integration test subclass.
    integrationTestVersions.forEach { (className, versions) ->
        systemProperties["$className.agpVersion"] = versions.first
        systemProperties["$className.gradleVersion"] = versions.second
    }
}

// Separate source set for heavy E2E tests that build the full testapp against multiple AGP versions.
// Lives in src/e2eTest/kotlin/ — fully independent from the unit/integration test source set.
val e2eTest by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

configurations[e2eTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[e2eTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    "e2eTestImplementation"(gradleTestKit())
}

// Pre-process the testapp into a clean directory using an allow-list.
// This excludes redundant build artifacts and IDE/Gradle internal folders.
// The Gradle wrapper is also excluded because E2E tests use GradleRunner.withGradleVersion().
val prepareTestApp by tasks.registering(Sync::class) {
    from("testapp") {
        include("app/src/**")
        include("app/build.gradle.kts")
        include("gradle/*.toml")
        include("gradle/*.properties")
        include("build.gradle.kts")
        include("settings.gradle.kts")
        include("gradle.properties")
    }
    into(layout.buildDirectory.dir("testapp-prepared"))
}

val e2eTestTask by tasks.registering(Test::class) {
    description = "Runs end-to-end tests that build the full testapp against multiple AGP versions"
    group = "verification"
    testClassesDirs = e2eTest.output.classesDirs
    classpath = e2eTest.runtimeClasspath

    configureTestKitDefaults()

    // Wire the prepared directory to inputs and system properties via Providers.
    // This implicitly handles the task dependency on prepareTestApp.
    val testAppDirProvider = prepareTestApp.map { it.destinationDir }
    inputs.dir(testAppDirProvider)
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("testapp")

    // Pass as a system property lazily to maintain configuration cache compatibility.
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-Dtestapp.dir=${testAppDirProvider.get().absolutePath}")
    })

    // Inject AGP/Gradle version pairs as system properties for each e2e subclass.
    e2eTestVersions.forEach { (className, versions) ->
        systemProperties["$className.agpVersion"] = versions.first
        systemProperties["$className.gradleVersion"] = versions.second
    }
}

tasks.named("check") { dependsOn(integrationTestTask, e2eTestTask) }

publishing {
    repositories {
        maven {
            name = "local"
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
