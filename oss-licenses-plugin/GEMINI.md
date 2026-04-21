# Gemini Developer Guide: OSS Licenses Plugin

This document provides essential information for AI agents and developers working on the `oss-licenses-plugin` and its tests.

## Test Architecture

The project uses a three-tier testing strategy: fast unit tests for task logic, a GradleTestKit integration matrix for plugin-on-Gradle behavior, and heavy end-to-end tests that build the full testapp against the AGP/Gradle matrix.

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
*   **Matrix:** Defined in `build.gradle.kts` as `integrationOnlyVersions + e2eVersions` (integration covers the full backward-compat range).
*   **Execution:** `./gradlew integrationTestTask` (also runs as part of `check`).

### 3. End-to-End Tests (`src/e2eTest/`)
These tests build the full standalone testapp under `testapp/` against multiple AGP/Gradle versions, exercising the plugin from the outside just like a real downstream project.

*   **File:** `EndToEndTest.kt`
*   **Mechanism:** Uses `GradleTestKit` to run `./gradlew build` inside a copy of `testapp/` whose `libs.versions.toml` has been patched to the matrix-selected AGP (and, on AGP 8.x, the Kotlin block in `app/build.gradle.kts` is rewritten to the legacy KGP form).
*   **Focus:** Real consumer build — verifies the testapp compiles, resources shrink, and the plugin integrates cleanly.
*   **Matrix:** Defined in `build.gradle.kts` as `e2eVersions` (modern AGP only — the e2e path is heavy).
*   **Execution:** `./gradlew e2eTestTask` (also runs as part of `check`).

---

## Testing Infrastructure & Matrix

The complexity of testing across multiple AGP/Gradle versions is managed through a centralized configuration in `build.gradle.kts`.

### Centralized Version Matrix
The `build.gradle.kts` file is the **single source of truth** for all versions.
*   It defines two maps of version pairs: `e2eVersions` (modern AGPs exercised by both integration and e2e) and `integrationOnlyVersions` (older AGPs kept for backward-compat coverage, integration-only).
*   It configures the manually declared test subclasses by injecting these version values as **system properties** (e.g., `IntegrationTest_AGP74.agpVersion`, `EndToEndTest_AGP_STABLE.gradleVersion`).
*   To add a new version: add the entry to `e2eVersions` (if it should run e2e) or `integrationOnlyVersions` (integration-only), then create the matching empty subclass in `IntegrationTest.kt` and/or `EndToEndTest.kt`. Keep the key in sync with the `agp-version-key` matrices in `.github/workflows/oss-licenses.yml`.

### Test Isolation
To allow safe parallel execution, each test subclass uses a dedicated `TestKit` directory (set via `.withTestKitDir()`). This prevents different AGP versions from clobbering each other's Gradle User Home caches.

### JVM & Toolchain Management
To ensure tests run consistently regardless of the host environment, the build script uses the `JavaToolchainService` to locate a Java 21 JDK. This path is injected into the tests via the `java21_home` system property when a test task actually executes.

---

## Common Tasks

| Task | Command | Description |
| :--- | :--- | :--- |
| **Full Check** | `./gradlew check` | Runs unit, integration, and e2e matrices. |
| **Unit only** | `./gradlew test` | Runs internal plugin unit tests only. |
| **Integration only** | `./gradlew integrationTestTask` | Runs the GradleTestKit integration matrix. |
| **E2E only** | `./gradlew e2eTestTask` | Runs the testapp build e2e matrix. Requires `ANDROID_HOME` or a `local.properties` with `sdk.dir=`. |
| **Publish** | `./gradlew publishAllPublicationsToLocalRepository` | Publishes the plugin to the internal `build/repo`. |
