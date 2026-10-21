plugins {
    id("com.android.library")
    id("com.google.gms.google-services")
}

import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

android {
    namespace = "com.example.mylibrary"
    compileSdk = 33

    defaultConfig {
        minSdk = 24
    }
}

googleServices {
    missingGoogleServicesStrategy = MissingGoogleServicesStrategy.ERROR
}
