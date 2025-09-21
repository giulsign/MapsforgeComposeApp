plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "it.example.mapsforgecompose"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.example.mapsforgecompose"
        minSdk = 27
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        // Compiler compatibile con Kotlin 1.9.25 (vedi note)
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    packagingOptions {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
    }
}

dependencies {

    implementation(platform("androidx.compose:compose-bom:2025.09.00"))


    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.10.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-test-manifest")
    implementation("androidx.compose.ui:ui-test-junit4")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")


    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")


    implementation("com.google.android.gms:play-services-location:21.1.0")


    implementation("org.mapsforge:mapsforge-map-android:0.25.0")
    implementation("org.mapsforge:mapsforge-map-reader:0.25.0")

    implementation("junit:junit:4.13.2")
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.espresso:espresso-core:3.6.1")



    implementation("org.mapsforge:mapsforge-map:0.25.0")
    implementation("org.mapsforge:mapsforge-themes:0.25.0")

    implementation("com.caverock:androidsvg:1.4")
}
