plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.chaquo.python")
}

android {
    namespace = "com.gammatunes.app"
    compileSdk = 35
    // Явно фиксируем версию, совпадающую с той, что установлена в Nix SDK
    // (см. flake.nix / shell.nix). Nix store доступен только на чтение,
    // поэтому AGP не может сам докачать/доустановить недостающие build-tools —
    // если версию не указать явно, он попробует подобрать её сам и упадёт.
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.gammatunes.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.2-unstable"

        ndk {
            // Python-интерпретатор Chaquopy — нативный компонент, нужно явно
            // указать ABI. arm64-v8a покрывает почти все современные телефоны,
            // x86_64 — эмуляторы на процессорах Intel/AMD.
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

// Настройки встроенного Python-бэкенда (см. src/main/python/backend_server.py).
// Требует Python на машине СБОРКИ (версия должна совпадать: 3.11 — см. flake.nix/shell.nix,
// там добавлен pkgs.python311). На самом телефоне отдельный Python не нужен —
// интерпретатор и все pip-пакеты Chaquopy упаковывает прямо в APK.
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

    // Compose BOM держит версии всех compose-артефактов в согласии
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Сеть
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Изображения
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Аудио-плеер
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    // Нужен для DefaultHttpDataSource — прокидываем в ExoPlayer точные
    // HTTP-заголовки (User-Agent), с которыми yt-dlp получил ссылку на
    // поток, иначе YouTube троттлит/обрывает соединение через ~15 секунд.
    implementation("androidx.media3:media3-datasource:1.4.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
