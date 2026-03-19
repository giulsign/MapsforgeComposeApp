pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    plugins {
        id("com.android.application") version "9.1.0"
        id("com.android.library") version "9.1.0"
        id("org.jetbrains.kotlin.android") version "2.2.10"
        id("com.mikepenz.aboutlibraries.plugin") version "11.1.0"
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "fourSTLPositionMarker"
include(":app")
