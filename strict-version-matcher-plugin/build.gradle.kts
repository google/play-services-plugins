plugins {
    id("groovy")
    id("java-gradle-plugin")
    id("org.jetbrains.kotlin.jvm") version "2.4.0"
    id("com.gradle.plugin-publish") version "2.1.1"
}

group = "com.google.android.gms"
version = "1.2.4"
description = "Gradle plug-in to enforce version ranges for Google Play services and Firebase dependencies."

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
    implementation(localGroovy())
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.4.5")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("com.google.guava:guava:33.6.0-jre")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.4.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

kotlin {
    jvmToolchain(11)
}

publishing {
  repositories {
        maven {
          url = uri(layout.buildDirectory.dir("repo"))
        }
    }
    publications {
        create<MavenPublication>("pluginMaven") {
            artifactId = "strict-version-matcher-plugin"
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
