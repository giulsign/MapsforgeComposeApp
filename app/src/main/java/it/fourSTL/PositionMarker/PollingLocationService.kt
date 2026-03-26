package it.fourSTL.PositionMarker

import android.content.Context
import android.location.Location
import android.util.Log
import it.fourSTL.PositionMarker.firebase.FirebaseResult
import it.fourSTL.PositionMarker.firebase.SessionRole
import it.fourSTL.PositionMarker.firebase.SharedLocation
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * HTTP Polling location sharing service.
 *
 * Funzionamento:
 *  - create/join/leave → chiamate HTTP POST singole
 *  - updateLocation    → HTTP POST ogni volta che la posizione cambia
 *  - observeSession    → polling GET ogni POLL_INTERVAL_MS millisecondi
 *
 * Non richiede WebSocket né Firebase. Funziona su qualsiasi hosting PHP.
 */
class PollingLocationService private constructor(private val context: Context) {

    companion object {
        private const val TAG = "PollingService"
        private const val PREF_NAME = "polling_location_prefs"
        private const val KEY_DEVICE_ID = "device_id"

        // ----------------------------------------------------------------
        // CAMBIA QUESTO con l'URL reale sul tuo hosting IONOS
        // Esempio: "https://tuosito.it/api/location/api.php"
        // ----------------------------------------------------------------
        private const val API_URL = "https://pointmarker.it/api/location/api.php"

        // Intervallo di polling in millisecondi (5 secondi = buon compromesso
        // tra reattività e consumo batteria/banda)
        private const val POLL_INTERVAL_MS = 5_000L

        // Timeout HTTP
        private const val TIMEOUT_SEC = 10L

        @Volatile
        private var INSTANCE: PollingLocationService? = null

        fun getInstance(context: Context): PollingLocationService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PollingLocationService(context.applicationContext).also {
                    INSTANCE = it
                    Log.d(TAG, "PollingLocationService singleton initialized")
                }
            }
        }
    }

    // --- HTTP client ---
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // --- State ---
    private var currentSessionId: String? = null
    private val currentDeviceId: String = getOrCreateDeviceId()
    private var currentRole: SessionRole = SessionRole.NONE
    private var currentDeviceName: String = ""
    private var currentColor: String = "#3b82f6"

    // --- Device ID ---

    private fun getOrCreateDeviceId(): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    fun getDeviceId(): String = currentDeviceId

    // --- HTTP helpers ---

    private suspend fun post(payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val body = payload.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(API_URL)
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
                ?: throw IOException("Empty response body")
            JSONObject(responseBody)
        }
    }

    private suspend fun get(params: Map<String, String>): JSONObject = withContext(Dispatchers.IO) {
        val urlBuilder = API_URL.toHttpUrlOrNull()!!.newBuilder()
        params.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
                ?: throw IOException("Empty response body")
            JSONObject(responseBody)
        }
    }

    // --- Public API (identica a PollingLocationService) ---

    suspend fun createSession(hostName: String): FirebaseResult<String> {
        return try {
            val response = post(JSONObject().apply {
                put("action", "create_session")
                put("deviceId", currentDeviceId)
                put("deviceName", hostName)
            })

            if (response.optString("type") == "SESSION_CREATED") {
                val sessionId = response.getString("sessionId")
                currentSessionId = sessionId
                currentRole = SessionRole.HOST
                currentDeviceName = hostName
                Log.d(TAG, "Session created: $sessionId")
                FirebaseResult.Success(sessionId)
            } else {
                val msg = response.optString("message", "Unknown error")
                FirebaseResult.Error(msg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating session", e)
            FirebaseResult.Error("Failed to create session: ${e.message}", e)
        }
    }

    suspend fun joinSession(sessionId: String, guestName: String): FirebaseResult<Boolean> {
        return try {
            val response = post(JSONObject().apply {
                put("action", "join_session")
                put("sessionId", sessionId)
                put("deviceId", currentDeviceId)
                put("deviceName", guestName)
            })

            when (response.optString("type")) {
                "SESSION_JOINED" -> {
                    currentSessionId = sessionId
                    currentRole = SessionRole.GUEST
                    currentDeviceName = guestName
                    currentColor = response.optString("color", "#3b82f6")
                    Log.d(TAG, "Joined session: $sessionId as $guestName ($currentColor)")
                    FirebaseResult.Success(true)
                }
                "ERROR" -> {
                    FirebaseResult.Error(response.optString("message", "Unknown error"))
                }
                else -> FirebaseResult.Error("Unexpected response")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error joining session", e)
            FirebaseResult.Error("Failed to join session: ${e.message}", e)
        }
    }

    suspend fun updateLocation(location: Location): FirebaseResult<Boolean> {
        val sessionId = currentSessionId
            ?: return FirebaseResult.Error("No active session")

        return try {
            val response = post(JSONObject().apply {
                put("action", "update_location")
                put("sessionId", sessionId)
                put("deviceId", currentDeviceId)
                put("latitude", location.latitude)
                put("longitude", location.longitude)
                put("altitude", if (location.hasAltitude()) location.altitude else 0.0)
            })

            if (response.optString("type") == "LOCATION_UPDATED") {
                FirebaseResult.Success(true)
            } else {
                FirebaseResult.Error(response.optString("message", "Update failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating location", e)
            FirebaseResult.Error("Failed to update location: ${e.message}", e)
        }
    }

    fun observeSession(sessionId: String): Flow<Map<String, SharedLocation>> = callbackFlow {
        Log.d(TAG, "Starting polling for session: $sessionId (every ${POLL_INTERVAL_MS}ms)")

        val pollingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val response = get(mapOf(
                        "action"    to "get_locations",
                        "sessionId" to sessionId,
                        "deviceId"  to currentDeviceId
                    ))

                    when (response.optString("type")) {
                        "LOCATIONS_UPDATE" -> {
                            val locJson = response.optJSONObject("locations") ?: JSONObject()
                            val locations = mutableMapOf<String, SharedLocation>()

                            for (key in locJson.keys()) {
                                val loc = locJson.getJSONObject(key)
                                locations[key] = SharedLocation(
                                    deviceId   = loc.optString("deviceId"),
                                    deviceName = loc.optString("deviceName"),
                                    latitude   = loc.optDouble("latitude", 0.0),
                                    longitude  = loc.optDouble("longitude", 0.0),
                                    altitude   = loc.optDouble("altitude", 0.0),
                                    timestamp  = loc.optLong("timestamp", 0L),
                                    active     = loc.optBoolean("active", true),
                                    color      = loc.optString("color", "#3b82f6")
                                )
                            }

                            trySend(locations)
                        }

                        "SESSION_CLOSED" -> {
                            Log.d(TAG, "Session closed by server")
                            close()
                            return@launch
                        }

                        "ERROR" -> {
                            Log.w(TAG, "Poll error: ${response.optString("message")}")
                        }
                    }
                } catch (e: Exception) {
                    // Errori di rete transitori: logga e riprova al prossimo ciclo
                    Log.w(TAG, "Poll failed (will retry): ${e.message}")
                }

                delay(POLL_INTERVAL_MS)
            }
        }

        awaitClose {
            Log.d(TAG, "Stopped polling session: $sessionId")
            pollingJob.cancel()
        }
    }

    suspend fun leaveSession(): FirebaseResult<Boolean> {
        val sessionId = currentSessionId
            ?: return FirebaseResult.Error("No active session")

        return try {
            val response = post(JSONObject().apply {
                put("action", "leave_session")
                put("sessionId", sessionId)
                put("deviceId", currentDeviceId)
                put("role", currentRole.name)
            })

            currentSessionId = null
            currentRole = SessionRole.NONE
            currentDeviceName = ""

            FirebaseResult.Success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error leaving session", e)
            FirebaseResult.Error("Failed to leave session: ${e.message}", e)
        }
    }

    // --- Utility ---

    fun getCurrentSessionId(): String? = currentSessionId
    fun getCurrentRole(): SessionRole = currentRole
    fun isInSession(): Boolean = currentSessionId != null

    fun getDebugInfo(): String = """
        Device ID:    ${currentDeviceId.take(8)}...
        Device Name:  $currentDeviceName
        Session ID:   ${currentSessionId ?: "None"}
        Role:         $currentRole
        Color:        $currentColor
        Poll interval: ${POLL_INTERVAL_MS}ms
        API URL:      $API_URL
    """.trimIndent()
}