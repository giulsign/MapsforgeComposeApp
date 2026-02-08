package it.fourSTL.PositionMarker.firebase

import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.PropertyName

@IgnoreExtraProperties
data class SharedLocation(
    @PropertyName("deviceId") val deviceId: String = "",
    @PropertyName("deviceName") val deviceName: String = "",
    @PropertyName("latitude") val latitude: Double = 0.0,
    @PropertyName("longitude") val longitude: Double = 0.0,
    @PropertyName("altitude") val altitude: Double = 0.0,
    @PropertyName("timestamp") val timestamp: Long = 0L,
    @PropertyName("active") val active: Boolean = true,
    @PropertyName("color") val color: String = "#ef4444" // Colore marker
) {
    // EMPTY CONSTRUCTOR
    constructor() : this("", "", 0.0, 0.0, 0.0, 0L, true, "#ef4444")

    fun toMap(): Map<String, Any> = mapOf(
        "deviceId" to deviceId,
        "deviceName" to deviceName,
        "latitude" to latitude,
        "longitude" to longitude,
        "altitude" to altitude,
        "timestamp" to timestamp,
        "active" to active,
        "color" to color
    )
}

@IgnoreExtraProperties
data class SharingSession(
    @PropertyName("sessionId") val sessionId: String = "",
    @PropertyName("hostId") val hostId: String = "",
    @PropertyName("hostName") val hostName: String = "",
    @PropertyName("createdAt") val createdAt: Long = 0L,
    @PropertyName("maxGuests") val maxGuests: Int = 5,
    @PropertyName("isActive") val isActive: Boolean = true
) {
    constructor() : this("", "", "", 0L, 5, true)

    fun toMap(): Map<String, Any> = mapOf(
        "sessionId" to sessionId,
        "hostId" to hostId,
        "hostName" to hostName,
        "createdAt" to createdAt,
        "maxGuests" to maxGuests,
        "isActive" to isActive
    )
}

data class DeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val color: String,
    val isHost: Boolean
)

enum class SessionRole {
    HOST, GUEST, NONE
}

sealed class FirebaseResult<out T> {
    data class Success<T>(val data: T) : FirebaseResult<T>()
    data class Error(val message: String, val exception: Exception? = null) : FirebaseResult<Nothing>()
    object Loading : FirebaseResult<Nothing>()
}