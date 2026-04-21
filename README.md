# Google Play services Plugins

This project contains plugins to help with using Google Play services and
Firebase libraries.

## Getting Started

The plugins contained in this project are meant to work with the Google Play
services SDK.  See https://developers.google.com/android/guides/overview to
get started.

## Contents

### strict-version-matcher-plugin

Helps with managing cross-library version dependencies between components.

### oss-licenses-plugin

Helps apps to display open source software licenses and notices.

### google-services-plugin

Required for firebase applications on android, converts google-services.json to a resource file for use by the app, and references the code in strict-version-matcher.

## Security & Maintenance

### Ratchet

This project uses [Ratchet](https://github.com/sethvargo/ratchet) to ensure all GitHub Actions are pinned to immutable commit SHAs. This helps protect against supply chain attacks by ensuring that the actions used in CI/CD workflows are exactly the versions intended.

Developers are encouraged to run `ratchet pin .github/workflows/*.yml` locally before submitting pull requests that introduce or update GitHub Actions.

