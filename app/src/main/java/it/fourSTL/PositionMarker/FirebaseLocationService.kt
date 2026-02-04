package it.fourSTL.PositionMarker

import android.content.Context
import android.location.Location
import android.util.Log
import com.google.firebase.database.*
import it.fourSTL.PositionMarker.firebase.FirebaseResult
import it.fourSTL.PositionMarker.firebase.SessionRole
import it.fourSTL.PositionMarker.firebase.SharedLocation
import it.fourSTL.PositionMarker.firebase.SharingSession
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * 🔧 VERSIONE CORRETTA - Singleton con fix per HOST visibility
 */
class FirebaseLocationService private constructor(private val context: Context) {

    private val database = FirebaseConfig.getDatabase()
    private val sessionsRef = database.getReference("sharing_sessions")

    private var currentSessionId: String? = null
    private var currentDeviceId: String = getOrCreateDeviceId()
    private var currentRole: SessionRole = SessionRole.NONE
    private var currentDeviceName: String = ""  // ✅ AGGIUNTO: Memorizza il nome del device

    companion object {
        private const val TAG = "FirebaseLocationService"
        private const val PREF_NAME = "firebase_location_prefs"
        private const val KEY_DEVICE_ID = "device_id"

        private val MARKER_COLORS = listOf(
            "#ef4444", "#f59e0b", "#10b981",
            "#3b82f6", "#8b5cf6", "#ec4899"
        )

        // ✅ SINGLETON
        @Volatile
        private var INSTANCE: FirebaseLocationService? = null

        fun getInstance(context: Context): FirebaseLocationService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirebaseLocationService(context.applicationContext).also {
                    INSTANCE = it
                    Log.d(TAG, "🔧 FirebaseLocationService singleton initialized")
                }
            }
        }
    }

    // ========== DEVICE ID ==========

    private fun getOrCreateDeviceId(): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)

        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }

        return deviceId
    }

    fun getDeviceId(): String = currentDeviceId

    // ========== CREATE SESSION (HOST) ==========

    suspend fun createSession(hostName: String): FirebaseResult<String> {
        return try {
            val sessionId = generateSessionCode()
            val session = SharingSession(
                sessionId = sessionId,
                hostId = currentDeviceId,
                hostName = hostName,
                createdAt = System.currentTimeMillis(),
                maxGuests = 5,
                isActive = true
            )

            sessionsRef.child(sessionId).setValue(session.toMap()).await()

            // ✅ FIX: Crea subito l'entry host con posizione iniziale
            val initialHostLocation = SharedLocation(
                deviceId = currentDeviceId,
                deviceName = hostName,
                latitude = 0.0,  // Verrà aggiornata da updateLocation
                longitude = 0.0,
                altitude = 0.0,
                timestamp = System.currentTimeMillis(),
                active = true,
                color = "#22c55e"  // Verde per host
            )

            sessionsRef.child(sessionId)
                .child("host")
                .setValue(initialHostLocation.toMap())
                .await()

            currentSessionId = sessionId
            currentRole = SessionRole.HOST
            currentDeviceName = hostName  // ✅ Memorizza il nome

            Log.d(TAG, "✅ Session created: $sessionId (Host: $hostName)")
            FirebaseResult.Success(sessionId)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creating session", e)
            FirebaseResult.Error("Failed to create session: ${e.message}", e)
        }
    }

    // ========== JOIN SESSION (GUEST) ==========

    suspend fun joinSession(sessionId: String, guestName: String): FirebaseResult<Boolean> {
        return try {
            // Verifica se la sessione esiste
            val sessionSnapshot = sessionsRef.child(sessionId).get().await()

            if (!sessionSnapshot.exists()) {
                return FirebaseResult.Error("Session not found")
            }

            val session = sessionSnapshot.getValue(SharingSession::class.java)
            if (session?.isActive != true) {
                return FirebaseResult.Error("Session is not active")
            }

            // Conta i guest attuali
            val guestsSnapshot = sessionsRef.child(sessionId)
                .child("guests").get().await()

            val currentGuestCount = guestsSnapshot.childrenCount

            if (currentGuestCount >= session.maxGuests) {
                return FirebaseResult.Error("Session is full (max ${session.maxGuests} guests)")
            }

            // Aggiungi guest con colore casuale
            val guestColor = MARKER_COLORS.random()
            val guestLocation = SharedLocation(
                deviceId = currentDeviceId,
                deviceName = guestName,
                latitude = 0.0,
                longitude = 0.0,
                altitude = 0.0,
                timestamp = System.currentTimeMillis(),
                active = true,
                color = guestColor
            )

            sessionsRef.child(sessionId)
                .child("guests")
                .child(currentDeviceId)
                .setValue(guestLocation.toMap())
                .await()

            currentSessionId = sessionId
            currentRole = SessionRole.GUEST
            currentDeviceName = guestName  // ✅ Memorizza il nome

            Log.d(TAG, "✅ Joined session: $sessionId as $guestName")
            FirebaseResult.Success(true)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error joining session", e)
            FirebaseResult.Error("Failed to join session: ${e.message}", e)
        }
    }

    // ========== UPDATE LOCATION ==========

    suspend fun updateLocation(location: Location): FirebaseResult<Boolean> {
        val sessionId = currentSessionId ?: return FirebaseResult.Error("No active session")

        return try {
            val path = when (currentRole) {
                SessionRole.HOST -> "host"
                SessionRole.GUEST -> "guests/$currentDeviceId"
                SessionRole.NONE -> return FirebaseResult.Error("Invalid role")
            }

            // ✅ FIX: Includi TUTTI i campi necessari, non solo le coordinate
            val updates = mapOf(
                "deviceId" to currentDeviceId,           // ✅ AGGIUNTO
                "deviceName" to currentDeviceName,       // ✅ AGGIUNTO
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "altitude" to (if (location.hasAltitude()) location.altitude else 0.0),
                "timestamp" to System.currentTimeMillis(),
                "active" to true,
                "color" to if (currentRole == SessionRole.HOST) "#22c55e" else null  // ✅ AGGIUNTO
            ).filterValues { it != null } as Map<String, Any>

            sessionsRef.child(sessionId).child(path).updateChildren(updates).await()

            Log.d(TAG, "📍 Location updated: ${location.latitude}, ${location.longitude} (role: $currentRole, name: $currentDeviceName)")
            FirebaseResult.Success(true)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating location", e)
            FirebaseResult.Error("Failed to update location: ${e.message}", e)
        }
    }

    // ========== OBSERVE SESSION (REAL-TIME) ==========

    fun observeSession(sessionId: String): Flow<Map<String, SharedLocation>> = callbackFlow {
        Log.d(TAG, "👀 Starting to observe session: $sessionId")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val locations = mutableMapOf<String, SharedLocation>()

                // Host location
                snapshot.child("host").getValue(SharedLocation::class.java)?.let { host ->
                    if (host.active && host.deviceId.isNotEmpty()) {  // ✅ Verifica deviceId non vuoto
                        locations["host_${host.deviceId}"] = host.copy(color = "#22c55e")
                        Log.d(TAG, "📍 Host found: ${host.deviceName} at ${host.latitude}, ${host.longitude}")
                    }
                }

                // Guest locations
                var guestCount = 0
                snapshot.child("guests").children.forEach { guestSnapshot ->
                    guestSnapshot.getValue(SharedLocation::class.java)?.let { guest ->
                        if (guest.active && guest.deviceId.isNotEmpty()) {  // ✅ Verifica deviceId non vuoto
                            locations["guest_${guest.deviceId}"] = guest
                            guestCount++
                            Log.d(TAG, "📍 Guest found: ${guest.deviceName} at ${guest.latitude}, ${guest.longitude}")
                        }
                    }
                }

                Log.d(TAG, "📊 Total active locations: ${locations.size} (1 host + $guestCount guests)")
                trySend(locations)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "❌ Session observation cancelled", error.toException())
                close(error.toException())
            }
        }

        val ref = sessionsRef.child(sessionId)
        ref.addValueEventListener(listener)

        awaitClose {
            Log.d(TAG, "👋 Stopped observing session: $sessionId")
            ref.removeEventListener(listener)
        }
    }

    // ========== LEAVE SESSION ==========

    suspend fun leaveSession(): FirebaseResult<Boolean> {
        val sessionId = currentSessionId ?: return FirebaseResult.Error("No active session")

        return try {
            Log.d(TAG, "👋 Leaving session: $sessionId (role: $currentRole)")

            when (currentRole) {
                SessionRole.HOST -> {
                    // Host chiude l'intera sessione
                    sessionsRef.child(sessionId).child("isActive").setValue(false).await()
                    sessionsRef.child(sessionId).removeValue().await()
                    Log.d(TAG, "🛑 Host closed session $sessionId")
                }
                SessionRole.GUEST -> {
                    // Guest si rimuove dalla lista
                    sessionsRef.child(sessionId)
                        .child("guests")
                        .child(currentDeviceId)
                        .child("active")
                        .setValue(false)
                        .await()
                    Log.d(TAG, "👋 Guest left session $sessionId")
                }
                SessionRole.NONE -> {
                    Log.w(TAG, "⚠️ Attempted to leave session with NONE role")
                }
            }

            currentSessionId = null
            currentRole = SessionRole.NONE
            currentDeviceName = ""

            Log.d(TAG, "✅ Successfully left session")
            FirebaseResult.Success(true)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error leaving session", e)
            FirebaseResult.Error("Failed to leave session: ${e.message}", e)
        }
    }

    // ========== UTILITY ==========

    private fun generateSessionCode(): String {
        return (100000..999999).random().toString()
    }

    fun getCurrentSessionId(): String? = currentSessionId
    fun getCurrentRole(): SessionRole = currentRole
    fun isInSession(): Boolean = currentSessionId != null

    // Debug info
    fun getDebugInfo(): String {
        return """
            Device ID: ${currentDeviceId.take(8)}...
            Device Name: $currentDeviceName
            Session ID: ${currentSessionId ?: "None"}
            Role: $currentRole
            In Session: ${isInSession()}
        """.trimIndent()
    }
}
