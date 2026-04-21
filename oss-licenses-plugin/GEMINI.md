# Gemini Developer Guide: OSS Licenses Plugin

This document provides essential information for AI agents and developers working on the `oss-licenses-plugin` and its tests.

## Test Architecture

The project uses a two-tier testing strategy to ensure both internal logic and integration across the Android Gradle Plugin (AGP) and Gradle version matrix.

### 1. Unit Tests (`src/test/`)
These tests verify the logic of individual tasks and utility classes.

*   **Files:** `DependencyTaskTest.java`, `LicensesTaskTest.java`, `GoogleServicesLicenseTest.java`.
*   **Mechanism:** Uses `ProjectBuilder` to instantiate tasks in a lightweight, in-memory Gradle environment.
*   **Focus:** Task-specific logic, input/output handling, and edge cases.
*   **Execution:** `./gradlew test`

### 2. Integration Tests (`src/integrationTest/`)
These tests verify the plugin's integration with the Gradle lifecycle and its behavior in a real-world project structure. They live in a dedicated source set so the default `test` task stays fast.

*   **File:** `IntegrationTest.kt`
*   **Mechanism:** Uses `GradleTestKit` (`GradleRunner`) to execute the plugin against a set of static test projects.
*   **Focus:** Task wiring, Configuration Cache compatibility, and relocatability.
*   **Matrix:** Defined in `build.gradle.kts` (`integrationVersions`).
*   **Execution:** `./gradlew integrationTestTask` (also runs as part of `check`).

---

## Testing Infrastructure & Matrix

The complexity of testing across multiple AGP/Gradle versions is managed through a centralized configuration in `build.gradle.kts`.

### Centralized Version Matrix
The `build.gradle.kts` file is the **single source of truth** for all versions.
*   It defines a map (`integrationVersions`) of version pairs.
*   It configures the manually declared test subclasses by injecting these version values as **system properties** (e.g., `IntegrationTest_AGP74.agpVersion`).
*   To add a new version to the matrix: Add the entry to the map in `build.gradle.kts` and create the corresponding empty subclass in `IntegrationTest.kt`.

### Test Isolation
To allow safe parallel execution, each test subclass uses a dedicated `TestKit` directory (set via `.withTestKitDir()`). This prevents different AGP versions from clobbering each other's Gradle User Home caches.

### JVM & Toolchain Management
To ensure tests run consistently regardless of the host environment, the build script uses the `JavaToolchainService` to locate a Java 21 JDK. This path is injected into the tests via the `java21_home` system property when a test task actually executes.

---

## Common Tasks

| Task | Command | Description |
| :--- | :--- | :--- |
| **Full Check** | `./gradlew check` | Runs unit tests and the full integration matrix. |
| **Unit only** | `./gradlew test` | Runs internal plugin unit tests only. |
| **Integration only** | `./gradlew integrationTestTask` | Runs the GradleTestKit integration matrix. |
| **Publish** | `./gradlew publishAllPublicationsToLocalRepository` | Publishes the plugin to the internal `build/repo`. |
