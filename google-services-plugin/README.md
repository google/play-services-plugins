# Google Services Gradle Plugin

This plugin converts the google-services.json file for Firebase into a set of resources that the Firebase libraries can use. It also references the strict-version-matcher plugin, and will execute those checks as well.

## Usage

### Plugins DSL

Add the following to your project's settings.gradle:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
}
```

Apply the plugin in your app's build.gradle.kts:

```kotlin
plugins {
    id("com.google.gms.google-services") version "4.6.0"
}
```

Or in build.gradle:
```gradle
plugins {
    id 'com.google.gms.google-services' version '4.6.0'
}
```

### `google-services.json` location

Place the `google-services.json` file for your project in the `app/` directory.

Alternatively, you can use variant specific source-sets, for example: 
`debug/google-services.json`.

### Compatible Android plugins

The `com.google.gms.google-services` plugin can be applied to projects using
`com.android.application`, `com.android.library`, or `com.android.dynamic-feature`.

The plugin selects the `client` entry in `google-services.json` whose `package_name` matches the
module:

- For `com.android.application` and `com.android.dynamic-feature`, the variant's `applicationId`.
- For `com.android.library`, the library's `namespace`.

### Plugin configuration

Configure the plugin's behavior through the `googleServices` block in build.gradle.kts:

```kotlin
googleServices {
    // Disables checking of Google Play Services dependencies compatibility
    // Default: false
    
    disableVersionCheck = true 
    
    // Choose the behavior when google-services.json is missing:
    // Default: MissingGoogleServicesStrategy.ERROR
    // Possible options: IGNORE, WARN, ERROR  
    
    missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN
}
```

You can use `missingGoogleServicesStrategy` when some variants in your project
do not require Google Play Services and are missing the `google-services.json` file.

### Android Gradle plugin compatibility

The Google Services plugin requires AGP 7.3.0 or newer to work. 

## Legacy way

Add the following to your buildscript classpath, obtained from Google’s
[Maven repository](//developer.android.com/studio/build/dependencies#google-maven):

```gradle
classpath 'com.google.gms:google-services:4.6.0'
```

Apply the plugin in your app's build.gradle:

```gradle
apply plugin: 'com.google.gms.google-services'
```

These instructions are also documented
[online](//developers.google.com/android/guides/versioning)
