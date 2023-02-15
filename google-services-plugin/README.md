# Google Services Gradle Plugin

This plugin converts the google-services.json file for Firebase into a set of resources that the Firebase libraries can use. It also references the strict-version-matcher plugin, and will execute those checks as well.

## Usage

### Plugins DSL

Add the following to your project's settings.gradle:

```
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
}
```

Apply the plugin in your app's build.gradle.kts:

```
plugins {
    id("com.google.gms.google-services" version "5.0.0"
}
```

Or in build.gradle:
```
plugins {
    id 'com.google.gms.google-services' version '5.0.0'
}
```

### New in version 5.0.0 

#### `google-services.json` location

Place the `google-services.json` file for your project in the `src/main/google-services/` directory.

Alternatively, you can use variant specific source-sets, for example: 
`src/debug/google-services/google-services.json`.

#### Android Gradle plugin compatibility

The Google Services plugin requires AGP 7.3.0 or newer to work. 

## Legacy way

Add the following to your buildscript classpath, obtained from Google’s
[Maven repository](//developer.android.com/studio/build/dependencies#google-maven):

```
classpath 'com.google.gms:google-services:5.0.0'
```

Apply the plugin in your app's build.gradle:

```
apply plugin: 'com.google.gms.google-services'
```

These instructions are also documented
[online](//developers.google.com/android/guides/versioning)
