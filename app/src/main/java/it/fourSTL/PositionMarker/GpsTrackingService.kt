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
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.ui.platform.LocalContext

class GpsTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val trackPoints = mutableListOf<Location>()
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let {
                    Log.d(TAG, "New location: ${it.latitude}, ${it.longitude}")
                    trackPoints.add(it)
                    _trackPointsFlow.value = ArrayList(trackPoints)
                }
            }
        }
    }

    @SuppressLint("WakelockTimeout")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.d(TAG, "Starting tracking and acquiring WakeLock")
                Toast.makeText(this, "Inizio registrazione tracciato", Toast.LENGTH_SHORT).show()
                // Acquisisce il WakeLock per tenere attiva la CPU
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GpsTrackingService::lock").apply {
                    acquire()
                }

                trackPoints.clear()
                _trackPointsFlow.value = emptyList()
                startLocationUpdates()
                startForegroundService()
            }
            ACTION_STOP -> {
                Log.d(TAG, "Stopping tracking")
                Toast.makeText(this, "Fine registrazione tracciato", Toast.LENGTH_SHORT).show()
                stopTrackingAndReleaseResources()
            }
            ACTION_SAVE -> {
                val fileName = intent.getStringExtra(EXTRA_FILENAME)
                if (fileName != null && trackPoints.isNotEmpty()) {
                    Log.d(TAG, "Saving track to $fileName.gpx")
                    GpxUtils.saveTrackAsGpx(applicationContext, ArrayList(trackPoints), fileName)
                } else {
                    Log.w(TAG, "File name is null or no track points to save.")
                }
            }
        }
        return START_STICKY
    }

    private fun stopTrackingAndReleaseResources() {
        stopLocationUpdates()
        trackPoints.clear()
        _trackPointsFlow.value = emptyList()
        // Rilascia il WakeLock quando la registrazione si ferma
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released")
            }
        }
        stopForeground(true)
        stopSelf()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 5000
            fastestInterval = 2000
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

    override fun onDestroy() {
        // Assicura che le risorse vengano rilasciate anche se il servizio viene terminato in modo anomalo
        stopTrackingAndReleaseResources()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "GpsTrackingService"
        const val ACTION_START = "it.fourSTL.PositionMarker.action.START_TRACKING"
        const val ACTION_STOP = "it.fourSTL.PositionMarker.action.STOP_TRACKING"
        const val ACTION_SAVE = "it.fourSTL.PositionMarker.action.SAVE_TRACK"
        const val EXTRA_FILENAME = "it.fourSTL.PositionMarker.extra.FILENAME"

        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "gps_tracking_channel"

        private val _trackPointsFlow = MutableStateFlow<List<Location>>(emptyList())
        val trackPointsFlow = _trackPointsFlow.asStateFlow()
    }
}
