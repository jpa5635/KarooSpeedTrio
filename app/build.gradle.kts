import com.android.build.api.variant.impl.VariantOutputImpl

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jpa5635.speedtrio"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jpa5635.speedtrio"
        minSdk = 26
        targetSdk = 34
        // versionCode must keep increasing by 1 each release; Android compares nothing else
        // when deciding whether an APK is an upgrade.
        versionCode = 1
        versionName = "1.0.0"
    }

    // Same committed sideload keystore as KarooInsta360Ctl (Android's public debug
    // credentials, androiddebugkey / "android"): the same key on every build machine, so
    // updates install over the top. Not a secret, and not for anything published.
    signingConfigs {
        create("shared") {
            storeFile = file("shared-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            // R8 stays off, as in the Insta360 extension: the karoo-ext callback surface and
            // RemoteViews are the parts hardest to diagnose on a head unit if shrinking breaks them.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Kotlin sources live directly under src/main/kotlin (not src/main/java)
    sourceSets["main"].kotlin.srcDirs("src/main/kotlin")
}

// Name the output after what it is (karoo-speedtrio-1.0.0.apk) rather than app-release.apk.
// `outputFileName` is only on the impl class in AGP 8.13.2, hence the cast.
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is VariantOutputImpl) {
                output.outputFileName.set(
                    "karoo-speedtrio-${android.defaultConfig.versionName}.apk",
                )
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")

    // Karoo extension SDK; see settings.gradle.kts for the GitHub Packages credentials this needs.
    implementation("io.hammerhead:karoo-ext:1.1.9")

    // Flow-based wrappers around the KarooSystemService callbacks.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
