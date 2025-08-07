plugins {
  id("groovy")
    id("java-gradle-plugin")
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("com.gradle.plugin-publish") version "1.1.0"
}

group = "com.google.android.gms"
version = "0.10.7"

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
    implementation(gradleApi())
    implementation(localGroovy())
    implementation("com.android.tools.build:gradle:7.1.0")
    implementation("com.android.tools.build:gradle-api:7.1.0")
    implementation("com.google.protobuf:protobuf-java:3.19.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:4.1.0")
    testImplementation("com.google.guava:guava:31.0.1-jre")
    testImplementation("com.google.truth:truth:1.1.3")
    testImplementation("com.google.code.gson:gson:2.8.9")
}

publishing {
    repositories {
        maven {
            name = "localPluginRepository"
            url = uri("../local-plugin-repository")
        }
    }
    publications {
        create<MavenPublication>("pluginMaven") {
            artifactId = "oss-licenses-plugin"
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
