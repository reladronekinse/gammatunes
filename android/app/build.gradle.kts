plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.chaquo.python")
}

android {
    namespace = "com.gammatunes.app"
    compileSdk = 35




    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.gammatunes.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.3-stable"

        ndk {



            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

chaquopy {
    defaultConfig {
        version = "3.11"
        pip {
            install("ytmusicapi")
            install("yt-dlp")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")


    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")


    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")


    implementation("io.coil-kt:coil-compose:2.6.0")

    implementation("androidx.palette:palette-ktx:1.0.0")


    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")



    implementation("androidx.media3:media3-datasource:1.4.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
