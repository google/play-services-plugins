plugins {
    id("groovy")
    id("java-gradle-plugin")
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("com.gradle.plugin-publish") version "1.1.0"
}

group = "com.google.android.gms"
version = "0.10.9"

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
    compileOnly("com.android.tools.build:gradle:8.2.0")
    compileOnly("com.android.tools.build:gradle-api:8.2.0")
    implementation(gradleApi())
    implementation(localGroovy())
    implementation("com.google.protobuf:protobuf-java:3.19.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:4.1.0")
    testImplementation("com.google.guava:guava:31.0.1-jre")
    testImplementation("com.google.truth:truth:1.1.3")
    testImplementation("com.google.code.gson:gson:2.8.9")
    testImplementation("com.android.tools.build:gradle:8.2.0") {
        because("Needed for DependencyTaskTest.")
    }
}

val repo = layout.buildDirectory.dir("repo")
tasks.withType<Test>().configureEach {
    // Make sure that build/repo is created and that it is used as input for the test task.
    // Replace this with something less ugly if https://github.com/gradle/gradle/issues/34870 is fixed
    dependsOn("publish")
    inputs.files(
        repo.map {
            // Exclude maven-metadata.xml as they contain timestamps but have no effect on the test outcomes
            it.asFileTree.matching { exclude("**/maven-metadata.xml*") }
        }
    ).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("repo")

    systemProperties["plugin_version"] = project.version // value used by EndToEndTest.kt
    doFirst {
        // Inside doFirst to make sure that absolute path is not considered to be input to the task
        systemProperties["repo_path"] = repo.get().asFile.absolutePath // value used by EndToEndTest.kt
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
