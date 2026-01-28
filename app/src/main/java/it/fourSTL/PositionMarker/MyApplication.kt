package it.fourSTL.PositionMarker

import android.app.Application
import android.util.Log

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Inizializza Firebase
        FirebaseConfig.initialize(this)

        // Inizializza il singleton FirebaseLocationService
        FirebaseLocationService.getInstance(this)

        Log.d("MyApplication", "✅ Firebase services initialized")
    }
}