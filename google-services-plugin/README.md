# Google Services Gradle Plugin

This plugin converts the google-services.json file for Firebase into a set of resources that the Firebase libraries can use. It also contains a references to the strict-version-matching plugin, and will execute those checks as well. 

## To use

In your app's build.gradle:

```
apply plugin: 'com.google.gms.google-services'
```

In order to use this plugin, you will also need to add the following to your
buildscript classpath, obtained from Google’s
[Maven repository](//developer.android.com/studio/build/dependencies#google-maven):

```
classpath 'com.google.gms:google-services:4.2.0'
```

These instructions are also documented
[online](//developers.google.com/android/guides/versioning)
