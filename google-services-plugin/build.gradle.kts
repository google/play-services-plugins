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
    id("org.jetbrains.kotlin.jvm") version "1.7.22"
    id("com.gradle.plugin-publish") version "1.1.0"
}

group = "com.google.gms"
version = "4.4.3"

dependencies {
    compileOnly("com.android.tools.build:gradle-api:7.3.0")
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
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

kotlin {
    jvmToolchain(11)
}

tasks.withType<Test>().configureEach {
    // See GoogleServicesPluginTest.kt -> testResGeneration
    dependsOn("publishAllPublicationsToMavenRepository")
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
    afterEvaluate {
        publications.withType(MavenPublication::class.java) {
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
}
