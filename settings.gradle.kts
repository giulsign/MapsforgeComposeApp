pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    plugins {
        id("com.android.application") version "8.13.2"
        id("com.android.library") version "8.13.2"
        id("org.jetbrains.kotlin.android") version "1.9.25"
        id("com.mikepenz.aboutlibraries.plugin") version "11.1.0"
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "fourSTLPositionMarker"
include(":app")
