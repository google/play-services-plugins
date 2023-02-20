plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 24
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += listOf("foo", "bar")
    productFlavors {
        create("free") {
            dimension = "foo"
        }
        create("paid") {
            dimension = "foo"
        }

        create("one") {
            dimension = "bar"
        }

        create("two") {
            dimension = "bar"
        }
    }
}