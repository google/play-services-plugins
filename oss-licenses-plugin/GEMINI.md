# Gemini Developer Guide: OSS Licenses Plugin

This document provides essential information for AI agents and developers working on the `oss-licenses-plugin` and its tests.

## Test Architecture

The project uses a three-tier testing strategy to ensure both internal logic and full integration across the Android Gradle Plugin (AGP) and Gradle version matrix.

### 1. Unit Tests (`src/test/`)
These tests verify the logic of individual tasks and utility classes.

*   **Files:** `DependencyTaskTest.java`, `LicensesTaskTest.java`, `GoogleServicesLicenseTest.java`.
*   **Mechanism:** Uses `ProjectBuilder` to instantiate tasks in a lightweight, in-memory Gradle environment.
*   **Focus:** Task-specific logic, input/output handling, and edge cases.
*   **Execution:** `./gradlew test`

### 2. Integration Tests (`src/test/`)
These tests verify the plugin's integration with the Gradle lifecycle and its behavior in a real-world project structure.

*   **File:** `IntegrationTest.kt`
*   **Mechanism:** Uses `GradleTestKit` (`GradleRunner`) to execute the plugin against a set of static test projects.
*   **Focus:** Task wiring, Configuration Cache compatibility, and relocatability.
*   **Matrix:** Defined in `build.gradle.kts` (`integrationOnlyVersions` + `e2eVersions`).
*   **Execution:** `./gradlew test` (runs alongside unit tests).

### 3. End-to-End Tests (`src/e2eTest/`)
These are heavy tests that build and test a full Android application against a matrix of AGP and Gradle versions.

*   **File:** `EndToEndTest.kt`
*   **Mechanism:** Uses `GradleTestKit` to build and run the `testapp` across multiple versions.
*   **Focus:** Verifying the plugin's end-to-end behavior within a real Android project.
*   **Matrix:** Defined in `build.gradle.kts` (`e2eVersions`).
*   **Execution:** `./gradlew e2eTestTask` (also runs as part of `check` and `build`)

---

## Testing Infrastructure & Matrix

The complexity of testing across multiple AGP/Gradle versions is managed through a centralized configuration in `build.gradle.kts`.

### Centralized Version Matrix
The `build.gradle.kts` file is the **single source of truth** for all versions.
*   It defines maps (`e2eVersions`, `integrationOnlyVersions`) of version pairs.
*   It dynamically generates test subclasses by injecting these versions as **system properties** (e.g., `IntegrationTest_AGP74.agpVersion`).
*   To add a new version to the matrix: Add the entry to the map in `build.gradle.kts` and create the corresponding empty subclass in `IntegrationTest.kt` or `EndToEndTest.kt`.

### Test Isolation
To allow safe parallel execution, each test subclass uses a dedicated `TestKit` directory (set via `.withTestKitDir()`). This prevents different AGP versions from clobbering each other's Gradle User Home caches.

### JVM & Toolchain Management
To ensure tests run consistently regardless of the host environment:
1.  **Java 21 Injection:** The build script uses the `JavaToolchainService` to locate a Java 21 JDK. This path is injected into the tests via the `java21_home` system property.
2.  **JAVA_HOME Override:** Both `IntegrationTest` and `EndToEndTest` use `.withEnvironment(mapOf("JAVA_HOME" to java21Home))` to force the Gradle Runner to use the correct JVM.
3.  **Daemon Provisioning:** For older Gradle versions (like 8.11), the tests explicitly delete `gradle-daemon-jvm.properties` in the test workspace to prevent failing internal toolchain discovery.

---

## The Test Application (`testapp/`)

The `testapp/` directory is a standalone Gradle project used as the target for End-to-End tests.

### Dynamic Version Injection
The `EndToEndTest.kt` does not use the `testapp`'s `libs.versions.toml` as-is. Instead, it **rewrites the TOML file at runtime** to inject the specific AGP and Kotlin versions being tested in the current matrix iteration.

### Local Development Workflow
The `testapp` is configured to automatically pick up the locally built plugin.

1.  **Publish Locally:** The main build automatically publishes the plugin to `oss-licenses-plugin/build/repo` before running tests.
2.  **Standalone Run:** To run tests directly within the `testapp` environment:
    ```bash
    cd oss-licenses-plugin/testapp
    ./gradlew clean :app:test
    ```

---

## Common Tasks

| Task | Command | Description |
| :--- | :--- | :--- |
| **Full Check** | `./gradlew check` | Runs all tests (Unit, Integration, and E2E). |
| **Unit & Integration** | `./gradlew test` | Runs internal plugin tests and `IntegrationTest`. |
| **E2E Matrix** | `./gradlew e2eTestTask` | Runs the full matrix suite against the `testapp`. |
| **Publish** | `./gradlew publish` | Publishes the plugin to the internal `build/repo`. |
