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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GpsTrackingService : Service() {

    companion object {
        const val ACTION_SAVE_AND_STOP = "it.fourSTL.PositionMarker.action.SAVE_AND_STOP"
        private const val TAG = "GpsTrackingService"
        const val ACTION_START = "it.fourSTL.PositionMarker.action.START_TRACKING"
        const val ACTION_STOP = "it.fourSTL.PositionMarker.action.STOP_TRACKING"
        const val ACTION_SAVE = "it.fourSTL.PositionMarker.action.SAVE_TRACK"

        const val EXTRA_FILENAME = "it.fourSTL.PositionMarker.extra.FILENAME"
        const val EXTRA_TRACK_WIDTH = "it.fourSTL.PositionMarker.extra.TRACK_WIDTH" // 🆕

        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "gps_tracking_channel"

        private val _trackPointsFlow = MutableStateFlow<List<Location>>(emptyList())
        val trackPointsFlow = _trackPointsFlow.asStateFlow()

        private val _trackWidthFlow = MutableStateFlow<Float?>(null)
        val trackWidthFlow = _trackWidthFlow.asStateFlow()
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val trackPoints = mutableListOf<Location>()
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentTrackWidth: Float? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    trackPoints.add(location)
                    _trackPointsFlow.value = ArrayList(trackPoints)

                    val firebaseService = PollingLocationService.getInstance(applicationContext)
                    if (firebaseService.isInSession()) {
                        serviceScope.launch {
                            firebaseService.updateLocation(location)
                            Log.d(TAG, "📤 Location sent to Firebase from service")
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("WakelockTimeout")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.d(TAG, "Starting tracking and acquiring WakeLock")

                currentTrackWidth = intent.getFloatExtra(EXTRA_TRACK_WIDTH, -1f).let {
                    if (it > 0) it else null
                }
                _trackWidthFlow.value = currentTrackWidth

                val widthInfo = currentTrackWidth?.let { " (width: ${it}m)" } ?: ""
                Toast.makeText(
                    this,
                    "Start GPS tracking service$widthInfo",
                    Toast.LENGTH_SHORT
                ).show()

                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "GpsTrackingService::lock"
                ).apply {
                    acquire()
                }

                trackPoints.clear()
                _trackPointsFlow.value = emptyList()
                startLocationUpdates()
                startForegroundService()
            }

            ACTION_STOP -> {
                Log.d(TAG, "Stopping tracking")
                Toast.makeText(this, "Stop GPS tracking service", Toast.LENGTH_SHORT).show()
                stopTrackingAndReleaseResources()
            }

            ACTION_SAVE -> {
                val fileName = intent.getStringExtra(EXTRA_FILENAME)
                val saveWidth = intent.getFloatExtra(EXTRA_TRACK_WIDTH, -1f).let {
                    if (it > 0) it else currentTrackWidth
                }

                if (fileName != null && trackPoints.isNotEmpty()) {
                    Log.d(TAG, "Saving track to $fileName.gpx with width: $saveWidth")

                    GpxUtils.saveTrackAsGpx(
                        applicationContext,
                        ArrayList(trackPoints),
                        fileName,
                        trackWidthMeters = saveWidth // 🆕
                    )
                } else {
                    Log.w(TAG, "File name is null or no track points to save.")
                }
            }
            ACTION_SAVE_AND_STOP -> {
                val fileName = intent.getStringExtra(EXTRA_FILENAME)
                val saveWidth = intent.getFloatExtra(EXTRA_TRACK_WIDTH, -1f).let {
                    if (it > 0) it else currentTrackWidth
                }

                if (fileName != null && trackPoints.isNotEmpty()) {
                    Log.d(TAG, "Saving track to $fileName.gpx with width: $saveWidth then stopping")

                    // SAVE FILE
                    GpxUtils.saveTrackAsGpx(
                        applicationContext,
                        ArrayList(trackPoints),
                        fileName,
                        trackWidthMeters = saveWidth
                    )

                    // 2. SHOW CONFIRM
                    Toast.makeText(
                        this,
                        "✅ Track saved: $fileName.gpx",
                        Toast.LENGTH_LONG
                    ).show()

                    // 3. STOP SERVICE AFTER SAVE
                    stopTrackingAndReleaseResources()
                } else {
                    Log.w(TAG, "Cannot save: file name is null or no track points")
                    Toast.makeText(
                        this,
                        "❌ Cannot save track",
                        Toast.LENGTH_SHORT
                    ).show()
                    stopTrackingAndReleaseResources()
                }
            }

        }
        return START_STICKY
    }

    private fun stopTrackingAndReleaseResources() {
        stopLocationUpdates()
        trackPoints.clear()
        _trackPointsFlow.value = emptyList()

        currentTrackWidth = null
        _trackWidthFlow.value = null

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

        val widthInfo = currentTrackWidth?.let { " (${it}m)" } ?: ""
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Recording GPS tracking$widthInfo")
            .setContentText("The service is running in the foreground...")
            .setSmallIcon(R.drawable.ic_marker_blue)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "GPS recording channel"
            val descriptionText = "GPS recording channel description"
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

        stopTrackingAndReleaseResources()
        super.onDestroy()
    }
}
