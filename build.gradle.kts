// root build.gradle.kts (minimale)
plugins {
    // no global plugin available
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
