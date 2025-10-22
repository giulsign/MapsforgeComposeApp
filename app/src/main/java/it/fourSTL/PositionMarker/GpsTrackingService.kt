package it.fourSTL.PositionMarker

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices

class GpsTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val trackPoints = mutableListOf<Location>()

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let {
                    Log.d(TAG, "New location: ${it.latitude}, ${it.longitude}")
                    trackPoints.add(it)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.d(TAG, "Starting tracking")
                trackPoints.clear() // Pulisce i punti precedenti all'avvio di una nuova traccia
                startLocationUpdates()
                startForegroundService()
            }
            ACTION_STOP -> {
                Log.d(TAG, "Stopping tracking")
                stopLocationUpdates()
                Log.d(TAG, "Track points collected: ${trackPoints.size}. Points discarded.")
                trackPoints.clear() // L'utente ha fermato senza salvare
                stopForeground(true)
                stopSelf()
            }
            ACTION_SAVE -> {
                val fileName = intent.getStringExtra(EXTRA_FILENAME)
                if (fileName != null && trackPoints.isNotEmpty()) {
                    Log.d(TAG, "Saving track to $fileName.gpx")
                    // Passiamo una copia della lista per evitare problemi di concorrenza
                    GpxUtils.saveTrackAsGpx(applicationContext, ArrayList(trackPoints), fileName)
                } else {
                    Log.w(TAG, "File name is null or no track points to save.")
                }
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 5000 // 5 secondi
            fastestInterval = 2000 // 2 secondi
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun startForegroundService() {
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Registrazione GPS Attiva")
            .setContentText("La traccia è in corso di registrazione...")
            .setSmallIcon(R.drawable.ic_marker_blue)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Canale Registrazione GPS"
            val descriptionText = "Notifiche per il servizio di registrazione traccia GPS"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        private const val TAG = "GpsTrackingService"
        const val ACTION_START = "it.fourSTL.PositionMarker.action.START_TRACKING"
        const val ACTION_STOP = "it.fourSTL.PositionMarker.action.STOP_TRACKING"
        const val ACTION_SAVE = "it.fourSTL.PositionMarker.action.SAVE_TRACK"
        const val EXTRA_FILENAME = "it.fourSTL.PositionMarker.extra.FILENAME"

        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "gps_tracking_channel"
    }
}
