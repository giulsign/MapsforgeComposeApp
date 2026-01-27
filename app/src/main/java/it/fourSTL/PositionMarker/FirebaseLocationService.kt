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

class FirebaseLocationService(private val context: Context) {

    private val database = FirebaseConfig.getDatabase()
    private val sessionsRef = database.getReference("sharing_sessions")

    private var currentSessionId: String? = null
    private var currentDeviceId: String = getOrCreateDeviceId()
    private var currentRole: SessionRole = SessionRole.NONE

    companion object {
        private const val TAG = "FirebaseLocationService"
        private const val PREF_NAME = "firebase_location_prefs"
        private const val KEY_DEVICE_ID = "device_id"

        private val MARKER_COLORS = listOf(
            "#ef4444", "#f59e0b", "#10b981",
            "#3b82f6", "#8b5cf6", "#ec4899"
        )
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

            currentSessionId = sessionId
            currentRole = SessionRole.HOST

            Log.d(TAG, "✅ Session created: $sessionId")
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

            val updates = mapOf(
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "altitude" to (if (location.hasAltitude()) location.altitude else 0.0),
                "timestamp" to System.currentTimeMillis(),
                "active" to true
            )

            sessionsRef.child(sessionId).child(path).updateChildren(updates).await()

            FirebaseResult.Success(true)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating location", e)
            FirebaseResult.Error("Failed to update location: ${e.message}", e)
        }
    }

    // ========== OBSERVE SESSION (REAL-TIME) ==========

    fun observeSession(sessionId: String): Flow<Map<String, SharedLocation>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val locations = mutableMapOf<String, SharedLocation>()

                // Host location
                snapshot.child("host").getValue(SharedLocation::class.java)?.let {
                    if (it.active) {
                        locations["host_${it.deviceId}"] = it.copy(color = "#22c55e") // Verde per host
                    }
                }

                // Guest locations
                snapshot.child("guests").children.forEach { guestSnapshot ->
                    guestSnapshot.getValue(SharedLocation::class.java)?.let { guest ->
                        if (guest.active) {
                            locations["guest_${guest.deviceId}"] = guest
                        }
                    }
                }

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
            ref.removeEventListener(listener)
        }
    }

    // ========== LEAVE SESSION ==========

    suspend fun leaveSession(): FirebaseResult<Boolean> {
        val sessionId = currentSessionId ?: return FirebaseResult.Error("No active session")

        return try {
            when (currentRole) {
                SessionRole.HOST -> {
                    // Host chiude l'intera sessione
                    sessionsRef.child(sessionId).child("isActive").setValue(false).await()
                    sessionsRef.child(sessionId).removeValue().await()
                }
                SessionRole.GUEST -> {
                    // Guest si rimuove dalla lista
                    sessionsRef.child(sessionId)
                        .child("guests")
                        .child(currentDeviceId)
                        .child("active")
                        .setValue(false)
                        .await()
                }
                SessionRole.NONE -> {}
            }

            currentSessionId = null
            currentRole = SessionRole.NONE

            Log.d(TAG, "✅ Left session successfully")
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
}

/*import android.content.Context
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
import kotlin.random.Random
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

class FirebaseLocationService(private val context: Context) {

    private val database = FirebaseConfig.getDatabase()
    private val sessionsRef = database.getReference("sharing_sessions")

    private var currentSessionId: String? = null
    private var currentDeviceId: String = getOrCreateDeviceId()
    private var currentRole: SessionRole = SessionRole.NONE

    companion object {
        private const val TAG = "FirebaseLocationService"
        private const val PREF_NAME = "firebase_location_prefs"
        private const val KEY_DEVICE_ID = "device_id"
        private const val MAX_CODE_ATTEMPTS = 10

        private val MARKER_COLORS = listOf(
            "#ef4444", "#f59e0b", "#10b981",
            "#3b82f6", "#8b5cf6", "#ec4899"
        )
    }

    // ========== DEVICE ID ==========

    private fun getOrCreateDeviceId(): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)

        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
            Log.d(TAG, "📱 Created new device ID: $deviceId")
        } else {
            Log.d(TAG, "📱 Using existing device ID: $deviceId")
        }

        return deviceId
    }

    fun getDeviceId(): String = currentDeviceId

    // ========== CREATE SESSION (HOST) - VERSIONE CORRETTA ==========

    suspend fun createSession(hostName: String): FirebaseResult<String> {
        return try {
            Log.d(TAG, "🎯 Starting session creation for host: $hostName")

            // Genera un codice univoco
            val sessionId = generateUniqueSessionCode()

            if (sessionId == null) {
                Log.e(TAG, "❌ Failed to generate unique session code after $MAX_CODE_ATTEMPTS attempts")
                return FirebaseResult.Error("Failed to generate unique session code. Please try again.")
            }

            Log.d(TAG, "✅ Generated unique session code: $sessionId")

            // Crea la sessione
            val session = SharingSession(
                sessionId = sessionId,
                hostId = currentDeviceId,
                hostName = hostName,
                createdAt = System.currentTimeMillis(),
                maxGuests = 5,
                isActive = true
            )

            // Crea l'host location iniziale
            val hostLocation = SharedLocation(
                deviceId = currentDeviceId,
                deviceName = hostName,
                latitude = 0.0,
                longitude = 0.0,
                altitude = 0.0,
                timestamp = System.currentTimeMillis(),
                active = true,
                color = "#22c55e" // Verde per host
            )

            // Salva in Firebase con struttura completa
            val sessionData = mutableMapOf<String, Any>(
                "sessionId" to sessionId,
                "hostId" to currentDeviceId,
                "hostName" to hostName,
                "createdAt" to System.currentTimeMillis(),
                "maxGuests" to 5,
                "isActive" to true,
                "host" to hostLocation.toMap(),
                "guests" to emptyMap<String, Any>()
            )

            sessionsRef.child(sessionId).setValue(sessionData).await()

            currentSessionId = sessionId
            currentRole = SessionRole.HOST

            Log.d(TAG, "✅✅✅ Session created successfully: $sessionId")
            Log.d(TAG, "📊 Session data: $sessionData")

            FirebaseResult.Success(sessionId)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creating session", e)
            FirebaseResult.Error("Failed to create session: ${e.localizedMessage}", e)
        }
    }

    // ========== GENERA CODICE UNIVOCO ==========

    private suspend fun generateUniqueSessionCode(): String? {
        repeat(MAX_CODE_ATTEMPTS) { attempt ->
            val code = generateSessionCode()
            Log.d(TAG, "🎲 Attempt ${attempt + 1}/$MAX_CODE_ATTEMPTS - Generated code: $code")

            try {
                // 🆕 Aggiungi timeout di 5 secondi
                val snapshot = withTimeout(5000L) {
                    sessionsRef.child(code).get().await()
                }

                if (!snapshot.exists()) {
                    Log.d(TAG, "✅ Code $code is unique!")
                    return code
                } else {
                    Log.d(TAG, "⚠️ Code $code already exists, retrying...")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "⏱️ Timeout checking code $code - assuming it's unique")
                return code // Assume sia unico se timeout
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error checking code $code: ${e.message}", e)
                // Continua al prossimo tentativo
            }
        }

        return null
    }

    private fun generateSessionCode(): String {
        // Genera un codice a 6 cifre
        return Random.nextInt(100000, 999999).toString()
    }

    // ========== JOIN SESSION (GUEST) ==========

    suspend fun joinSession(sessionId: String, guestName: String): FirebaseResult<Boolean> {
        return try {
            Log.d(TAG, "🔗 Attempting to join session: $sessionId as $guestName")

            // Verifica se la sessione esiste
            val sessionSnapshot = sessionsRef.child(sessionId).get().await()

            if (!sessionSnapshot.exists()) {
                Log.e(TAG, "❌ Session $sessionId not found")
                return FirebaseResult.Error("Session not found")
            }

            val session = sessionSnapshot.getValue(SharingSession::class.java)
            if (session?.isActive != true) {
                Log.e(TAG, "❌ Session $sessionId is not active")
                return FirebaseResult.Error("Session is not active")
            }

            // Conta i guest attuali
            val guestsSnapshot = sessionsRef.child(sessionId)
                .child("guests").get().await()

            val currentGuestCount = guestsSnapshot.childrenCount

            Log.d(TAG, "📊 Current guests: $currentGuestCount / ${session.maxGuests}")

            if (currentGuestCount >= session.maxGuests) {
                Log.e(TAG, "❌ Session is full")
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

            Log.d(TAG, "✅✅✅ Joined session: $sessionId as $guestName (color: $guestColor)")
            FirebaseResult.Success(true)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error joining session", e)
            FirebaseResult.Error("Failed to join session: ${e.localizedMessage}", e)
        }
    }

    // ========== UPDATE LOCATION ==========

    suspend fun updateLocation(location: Location): FirebaseResult<Boolean> {
        val sessionId = currentSessionId ?: run {
            Log.w(TAG, "⚠️ Cannot update location: No active session")
            return FirebaseResult.Error("No active session")
        }

        return try {
            val path = when (currentRole) {
                SessionRole.HOST -> "host"
                SessionRole.GUEST -> "guests/$currentDeviceId"
                SessionRole.NONE -> {
                    Log.w(TAG, "⚠️ Cannot update location: Invalid role")
                    return FirebaseResult.Error("Invalid role")
                }
            }

            val updates = mapOf(
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "altitude" to (if (location.hasAltitude()) location.altitude else 0.0),
                "timestamp" to System.currentTimeMillis(),
                "active" to true
            )

            sessionsRef.child(sessionId).child(path).updateChildren(updates).await()

            Log.d(TAG, "📍 Location updated: ${location.latitude}, ${location.longitude}")
            FirebaseResult.Success(true)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating location", e)
            FirebaseResult.Error("Failed to update location: ${e.localizedMessage}", e)
        }
    }

    // ========== OBSERVE SESSION (REAL-TIME) ==========

    fun observeSession(sessionId: String): Flow<Map<String, SharedLocation>> = callbackFlow {
        Log.d(TAG, "👀 Starting to observe session: $sessionId")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val locations = mutableMapOf<String, SharedLocation>()

                // Host location
                snapshot.child("host").getValue(SharedLocation::class.java)?.let {
                    if (it.active) {
                        locations["host_${it.deviceId}"] = it.copy(color = "#22c55e") // Verde per host
                        Log.d(TAG, "📍 Host location: ${it.latitude}, ${it.longitude}")
                    }
                }

                // Guest locations
                var guestCount = 0
                snapshot.child("guests").children.forEach { guestSnapshot ->
                    guestSnapshot.getValue(SharedLocation::class.java)?.let { guest ->
                        if (guest.active) {
                            locations["guest_${guest.deviceId}"] = guest
                            guestCount++
                            Log.d(TAG, "📍 Guest ${guest.deviceName}: ${guest.latitude}, ${guest.longitude}")
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

                    // Opzionale: rimuovi completamente dopo qualche secondo
                    // sessionsRef.child(sessionId).removeValue().await()

                    Log.d(TAG, "🛑 Host closed session $sessionId")
                }
                SessionRole.GUEST -> {
                    // Guest si rimuove dalla lista
                    sessionsRef.child(sessionId)
                        .child("guests")
                        .child(currentDeviceId)
                        .removeValue()
                        .await()

                    Log.d(TAG, "👋 Guest left session $sessionId")
                }
                SessionRole.NONE -> {
                    Log.w(TAG, "⚠️ Attempted to leave session with NONE role")
                }
            }

            currentSessionId = null
            currentRole = SessionRole.NONE

            Log.d(TAG, "✅ Successfully left session")
            FirebaseResult.Success(true)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error leaving session", e)
            FirebaseResult.Error("Failed to leave session: ${e.localizedMessage}", e)
        }
    }

    // ========== UTILITY ==========

    fun getCurrentSessionId(): String? = currentSessionId
    fun getCurrentRole(): SessionRole = currentRole
    fun isInSession(): Boolean = currentSessionId != null

    // Debug info
    fun getDebugInfo(): String {
        return """
            Device ID: $currentDeviceId
            Session ID: ${currentSessionId ?: "None"}
            Role: $currentRole
            In Session: ${isInSession()}
        """.trimIndent()
    }
}*/