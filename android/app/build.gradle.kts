plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.freetunnel.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.freetunnel.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 10000
        versionName = "1.0.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            // Public GitHub builds are installable without private signing secrets.
            // Play Store releases should replace this with a protected upload key.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.adguard.trusttunnel:trusttunnel-client-android:1.1.5-rc.6")
}
