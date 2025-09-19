pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    plugins {
        id("com.android.application") version "8.13.0"
        id("com.android.library") version "8.13.0"
        id("org.jetbrains.kotlin.android") version "1.9.25"
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MapsforgeComposeApp"
include(":app")
