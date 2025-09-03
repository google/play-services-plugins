import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
plugins {
    id("java-gradle-plugin")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("com.gradle.plugin-publish") version "1.1.0"
}

group = "com.google.gms"
version = "4.4.3"

dependencies {
    compileOnly("com.android.tools.build:gradle-api:8.0.0")
    implementation("com.google.android.gms:strict-version-matcher-plugin:1.2.4")
    implementation("com.google.code.gson:gson:2.8.5")
    implementation("com.google.guava:guava:27.0.1-jre")
    testImplementation("junit:junit:4.12")
    testImplementation("com.google.truth:truth:0.42")
}

gradlePlugin {
    plugins {
        create("googleServicesPlugin") {
            id = "com.google.gms.google-services"
            implementationClass = "com.google.gms.googleservices.GoogleServicesPlugin"
        }
    }
}

publishing {
    repositories {
        maven {
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}

java {
    targetCompatibility = JavaVersion.VERSION_11
    sourceCompatibility = JavaVersion.VERSION_11
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
    coreLibrariesVersion = "2.0.0"
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        languageVersion.set(KotlinVersion.KOTLIN_2_0)
    }
}

tasks.withType<Test>().configureEach {
    // See GoogleServicesPluginTest.kt -> testResGeneration
    dependsOn("publishAllPublicationsToMavenRepository")
    systemProperties["plugin_version"] = project.version // value used by GoogleServicesPluginTest.kt
    inputs.dir("src/test/testData") // contents used by GoogleServicesPluginTest.kt
}

tasks.withType<Jar>().configureEach {
    from("LICENSE")
}

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("pluginMaven") {
            artifactId = "google-services"
        }
    }
    publications.withType(MavenPublication::class.java).configureEach {
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
