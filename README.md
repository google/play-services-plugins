# Strict Version Matcher Gradle Plugin

This plugin scans a subset of Google Play services and Firebase dependencies
during the Bradle build. It raises an error when version resolution is specified
in the POM file but not honored by the Gradle build system. This can happen when
Gradle's default dependency resolution strategy is used because it uses the
highest referenced version even if it crossed the major version boundary.

## To use

In your app's build.gradle:

```
apply plugin: 'com.google.android.gms.strict-version-matcher-plugin'
```

In order to use this plugin, you will also need to add the following to your
buildscript classpath, obtained from Google’s
[Maven repository](//developer.android.com/studio/build/dependencies#google-maven):

```
classpath 'com.google.android.gms:strict-version-matcher-plugin:1.0.2'
```

These instructions are also documented
[online](//developers.google.com/android/guides/versioning)
