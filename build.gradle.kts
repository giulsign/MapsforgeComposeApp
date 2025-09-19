// root build.gradle.kts (minimale)
plugins {
    // nessun plugin globale obbligatorio qui — plugin dichiarati in settings.gradle.kts
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
