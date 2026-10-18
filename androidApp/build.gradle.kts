plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// Release signing comes from environment variables or ~/.gradle/gradle.properties, never from the repository.
// Without MORROW_KEYSTORE, assembleRelease still builds, unsigned. See RELEASE.md.
fun secret(name: String): String? = providers.environmentVariable(name).orNull ?: providers.gradleProperty(name).orNull
val releaseKeystore = secret("MORROW_KEYSTORE")

android {
    namespace = "app.morrow"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.morrow"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // Keep in step with MARKETING_VERSION and MORROW_VERSION_LABEL in iosApp/project.yml. Every upload to
        // Google Play needs a higher versionCode than the last.
        versionCode = 1
        versionName = "0.1.0-beta.1"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = secret("MORROW_KEYSTORE_PASSWORD")
                keyAlias = secret("MORROW_KEY_ALIAS")
                keyPassword = secret("MORROW_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        // VERSION_NAME, for the User-Agent.
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
    // LocationManagerCompat: a coarse fix from the platform, without Google Play Services.
    implementation(libs.androidx.core)
    implementation(libs.compose.foundation)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.compose)
    // Reads codes on the phone. ML Kit would add Google's telemetry and the INTERNET permission.
    implementation(libs.zxing.core)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
