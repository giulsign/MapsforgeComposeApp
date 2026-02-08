package it.fourSTL.PositionMarker

import android.app.Application
import android.util.Log

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // START FIREBASE
        FirebaseConfig.initialize(this)

        // START LOCATION SERVICE
        // Initialize the singleton FirebaseLocationService
        FirebaseLocationService.getInstance(this)

        Log.d("MyApplication", "✅ Firebase services initialized")
    }
}