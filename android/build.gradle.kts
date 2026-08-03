plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    // Начиная с Kotlin 2.0 Compose Compiler — отдельный плагин, а не часть Kotlin/AGP.
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    // Chaquopy встраивает интерпретатор Python прямо в APK, чтобы бэкенд
    // (ytmusicapi + yt-dlp) поднимался локально на телефоне, без ПК.
    id("com.chaquo.python") version "17.0.0" apply false
}
