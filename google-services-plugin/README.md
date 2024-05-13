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
    id("com.google.gms.google-services" version "4.4.1"
}
```

Or in build.gradle:
```
plugins {
    id 'com.google.gms.google-services' version '4.4.1'
}
```

### New in version 4.4.0 

#### `google-services.json` location

Place the `google-services.json` file for your project in the `app/` directory.

Alternatively, you can use variant specific source-sets, for example: 
`debug/google-services.json`.

#### Compatible Android plugins

The `com.google.gms.google-services` plugin can only be applied to projects with 
`com.android.application` or `com.android.dynamic-feature`, as it requires an `applicationId` 
to function.

The plugin is not compatible with plugins such as `com.android.library` that do not 
contain an `applicationId`.

#### Plugin configuration

Configure the plugin's behavior through the `googleServices` block in build.gradle.kts:

```
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

#### Android Gradle plugin compatibility

The Google Services plugin requires AGP 7.3.0 or newer to work. 

## Legacy way

Add the following to your buildscript classpath, obtained from Google’s
[Maven repository](//developer.android.com/studio/build/dependencies#google-maven):

```
classpath 'com.google.gms:google-services:4.4.1'
```

Apply the plugin in your app's build.gradle:

```
apply plugin: 'com.google.gms.google-services'
```

These instructions are also documented
[online](//developers.google.com/android/guides/versioning)
