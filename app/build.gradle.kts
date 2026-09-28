plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.raja2102598.budstiles"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.raja2102598.budstiles"
        minSdk = 26
        targetSdk = 34
        // The release workflow overrides these from the git tag and run number.
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("versionName") as String?) ?: "1.0.0"
    }

    // Release signing comes from the environment (GitHub Actions secrets), so
    // the key never lives in the repository. Without it, release builds are unsigned.
    val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
    val keystorePassword = System.getenv("SIGNING_PASSWORD")
    val releaseSigning = if (keystorePath != null && keystorePassword != null) {
        signingConfigs.create("release") {
            storeFile = file(keystorePath)
            storePassword = keystorePassword
            keyAlias = "buds-tiles"
            keyPassword = keystorePassword
        }
    } else {
        null
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = releaseSigning
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    testImplementation("junit:junit:4.13.2")
}
