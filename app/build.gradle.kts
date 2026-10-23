plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.itsme.hp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.itsme.hp"
        minSdk = 26          // Android 8.0 - batas bawah yang didukung
        targetSdk = 34       // Android 14; aplikasi tetap berjalan di Android 15/16+
        versionCode = 1
        versionName = "0.1"
    }

    signingConfigs {
        create("release") {
            // Diisi dari secret repo lewat workflow build (ITSME_KEYSTORE menunjuk ke
            // berkas keystore yang sudah didekode). Kalau keystore tidak ada, build
            // tetap jalan memakai kunci debug sebagai cadangan (lihat buildTypes).
            val ks = System.getenv("ITSME_KEYSTORE")
            if (!ks.isNullOrBlank() && file(ks).exists()) {
                storeFile = file(ks)
                storePassword = System.getenv("ITSME_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ITSME_KEY_ALIAS")
                keyPassword = System.getenv("ITSME_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Tanda tangani dengan kunci rilis kalau keystore tersedia; kalau tidak,
            // pakai kunci debug supaya APK tetap bisa dipasang (hanya kurang "tepercaya").
            val ks = System.getenv("ITSME_KEYSTORE")
            signingConfig = if (!ks.isNullOrBlank() && file(ks).exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
