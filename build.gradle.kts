// root build.gradle.kts (minimale)
plugins {
    id("com.android.application") version "9.0.0" apply false
    //id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.25" apply false
    //kotlin("plugin.serialization") version "1.9.25" apply false
    id("com.mikepenz.aboutlibraries.plugin") version "11.1.0" apply false
    // no global plugin available
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
