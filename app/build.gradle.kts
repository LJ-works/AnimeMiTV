plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val appVersionName = rootProject.extra["appVersionName"] as String
val appVersionCode = rootProject.extra["appVersionCode"] as Int
val releaseBuildRequested = gradle.startParameter.taskNames.any {
    it.substringAfterLast(':').contains("Release", ignoreCase = true)
}
val releaseSigningValues = mapOf(
    "ANDROID_KEYSTORE_PATH" to System.getenv("ANDROID_KEYSTORE_PATH"),
    "ANDROID_KEYSTORE_PASSWORD" to System.getenv("ANDROID_KEYSTORE_PASSWORD"),
    "ANDROID_KEY_ALIAS" to System.getenv("ANDROID_KEY_ALIAS"),
    "ANDROID_KEY_PASSWORD" to System.getenv("ANDROID_KEY_PASSWORD"),
)
if (releaseBuildRequested) {
    releaseSigningValues.forEach { (name, value) ->
        require(!value.isNullOrBlank()) { "$name is required for release builds" }
    }
}

android {
    namespace = "com.ljworks.animemitv"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.ljworks.animemitv"
        versionCode = appVersionCode
        versionName = appVersionName
        minSdk = 29
        targetSdk = 37

    }

    signingConfigs {
        create("release") {
            storeFile = releaseSigningValues["ANDROID_KEYSTORE_PATH"]?.let(::file)
            storePassword = releaseSigningValues["ANDROID_KEYSTORE_PASSWORD"]
            keyAlias = releaseSigningValues["ANDROID_KEY_ALIAS"]
            keyPassword = releaseSigningValues["ANDROID_KEY_PASSWORD"]
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.jsoup)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.json)
}