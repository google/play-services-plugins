# Gemini Instructions

Instructions in this `GEMINI.md` file are project-specific guidelines. They take precedence over general defaults for AI agents working in this repository.

## 1. Project Overview

This repository hosts Gradle plugins that improve the developer experience for the Google Play services SDK and Firebase.

- **Philosophy:** Plugins are optional enhancers, not requirements for using client libraries.
- **Source of Truth:** GitHub is the primary source of truth.
- **Structure:** The repository is a monorepo containing independent Gradle projects. Each plugin is its own standalone project with its own `build.gradle.kts`, `settings.gradle`, and Gradle wrapper.

### Plugins Overviews

#### google-services-plugin
- **Purpose:** Automates the integration of Firebase and Google Play Services configuration into Android applications.
- **Architecture:** Hooks into the Android Gradle Plugin (AGP) via the `ApplicationAndroidComponentsExtension` and `DynamicFeatureAndroidComponentsExtension`. It dynamically registers a `GoogleServicesTask` for every application variant.
- **Logic:** The plugin searches for `google-services.json` files using a hierarchical folder pattern (based on build types and product flavors). It parses the JSON to extract specific API keys, project IDs, and tracking IDs, then generates an Android XML resource file (`values.xml`) that the SDKs consume at runtime.
- **Integration:** By default, it integrates with the `strict-version-matcher-plugin` to perform dependency analysis and ensure version compatibility across Google-owned libraries.

#### oss-licenses-plugin
- **Purpose:** Automates the collection and attribution of open-source licenses for Android applications.
- **Architecture:** Primarily implemented in Groovy. It hooks into the AGP lifecycle to register a task pipeline consisting of `DependencyTask` and `LicensesTask`.
- **Logic:** `LicensesTask` resolves the license information for every dependency. It uses Gradle's `ArtifactResolutionQuery` and `ArtifactView` to fetch POM files and libraries lazily, ensuring full compatibility with the **Gradle Configuration Cache**. For Google Play Services and Firebase AARs, it employs a specialized extraction strategy to read embedded license JSON and text files.
- **Output:** It produces two specific raw resources—`third_party_licenses` and `third_party_licenses_metadata`—which are wired to the Android resources source set for consumption by the `play-services-oss-licenses` runtime library.

#### strict-version-matcher-plugin
- **Purpose:** Enforces version consistency for Google Play Services and Firebase dependencies to prevent runtime crashes caused by incompatible library versions.
- **Architecture:** A standalone utility that can be applied directly or used as a library by other plugins (like `google-services-plugin`).
- **Logic:** It inspects the final resolved dependency graph of a project. It compares the versions Gradle selected against the original version ranges specified in the artifacts' POM files. If Gradle's resolution upgrades a dependency across a major version boundary that violates the original constraints, the plugin fails the build with a descriptive error.

## 2. Technology Stack

- **Languages:** Kotlin (preferred for new logic and tests) or Java. The
`oss-licenses-plugin` is written in Groovy.
- **Build System:** Gradle with Kotlin DSL (`build.gradle.kts`) is the preferred standard. JDK 17 is the current build environment standard.

## 3. CI/CD & Security

Managed via GitHub Actions with modern best practices:
- **Workflows:**
  - `main.yml`: The primary CI workflow. It includes a `lint-and-check` job (Gradle Wrapper validation, Actionlint, and Ratchet) and executes a build matrix that independently runs `assemble`, `check`, and `test` for each plugin project.
  - `generate_release_rcs.yml`: Handles the generation of release candidates.
- **Security & Maintenance:**
  - **Version Pinning:** `sethvargo/ratchet` is used to enforce that all GitHub Actions are pinned to immutable SHA-256 checksums to protect against supply chain attacks.
  - **Dependabot:** GitHub Action updates are consolidated into a weekly grouped Pull Request to minimize noise.
  - **Linting:** All workflows are linted using `abcxyz/actions/lint-github-actions` (incorporating `actionlint` and `shellcheck`) to ensure syntax correctness.
- **Best Practices:** We use `gradle/actions/setup-gradle` for high-performance caching and automatic dependency graph submission.

## 4. Coding Conventions

### Branch Naming
- **Standard:** Branches should be named using the format `username/description` (e.g., `<username>/description`).

### License Headers
- **Requirement:** Every source file (`.kt`, `.java`, `.gradle.kts`, `.groovy`, `.sh`) must include an Apache 2.0 license header.
- **Format:** Use the standard Apache 2.0 block.
- **Copyright Text:** `Copyright <Year> Google LLC`
- **Year:** Use the year the file was created (first published). Do **not** use a range unless the content has significantly changed. Do **not** update the year solely for the sake of updating it.
- **Entity:** Always use **"Google LLC"** (never "Google Inc.").

### Versioning
- **Standard:** Follow [Semantic Versioning (SemVer)](https://semver.org/).
- **Management:** Versions are managed independently within each plugin's `build.gradle.kts` file.

## 5. Engineering Standards

- **Testing:** All changes must be verified with tests. `oss-licenses-plugin` requires verification against the full AGP/Gradle version matrix in `EndToEndTest.kt`.
- **Configuration Cache:** New tasks or refactors MUST maintain compatibility with the Gradle Configuration Cache. Use lazy properties and avoid direct project access during task execution.
- **Commit Messages:** Follow the [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) specification.
  - Format: `<type>[optional scope]: <description>`
  - Common types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`.
  - Use a `!` after the type/scope for breaking changes (e.g., `feat!: rewrite plugin architecture`).
- **Proactiveness:** When updating dependencies or AGP, verify the impact across all standalone plugin projects in the monorepo.
- **Local Ratchet Usage:** Developers are encouraged to run `ratchet pin .github/workflows/*.yml` locally before submitting PRs with new actions.
