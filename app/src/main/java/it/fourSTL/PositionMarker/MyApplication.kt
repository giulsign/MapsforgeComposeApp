package it.fourSTL.PositionMarker

import android.app.Application
import android.util.Log

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // START LOCATION SERVICE
        PollingLocationService.getInstance(this)

        Log.d("MyApplication", "✅ Location service initialized")
    }
}