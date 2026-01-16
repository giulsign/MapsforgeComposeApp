package it.fourSTL.PositionMarker

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec



// ==================== DATA CLASSES ====================

@Serializable
//@OptIn(InternalSerializationApi::class)
data class Group(
    val id: String,                    // UUID group
    val name: String,                  // Personalized group name
    val encryptionKey: String,         // AES-256 key (Base64)
    val createdAt: Long,               // Timestamp
    val isHost: Boolean,               // True if the thevice is host
    var hostAddress: String = "",      // address IP: host port
    val members: MutableList<GroupMember> = mutableListOf()
)

@Serializable
//@OptIn(InternalSerializationApi::class)
data class GroupMember(
    val deviceId: String,              // ID device
    val deviceName: String,            // name device (es. "Samsung Galaxy S21")
    val joinedAt: Long,                // Timestamp in
    var lastSeenAt: Long = 0L,         // Last position update
    var latitude: Double = 0.0,        // Last latitude
    var longitude: Double = 0.0,       // Last longitude
    var isOnline: Boolean = false      // Connection state
)

@Serializable
//@OptIn(InternalSerializationApi::class)
data class EncryptedGroupData(
    val groupId: String,
    val encryptedPayload: String,      // Encrypted data group (Base64)
    val iv: String                     // Initialization Vector per AES-GCM
)

// ==================== GROUP MANAGER CLASS ====================

class GroupManager(private val context: Context) {

    companion object {
        private const val TAG = "GroupManager"
        private const val PREFS_NAME = "group_sharing_prefs"
        private const val KEY_ACTIVE_GROUP = "active_group"
        private const val KEY_ALL_GROUPS = "all_groups"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_NAME = "device_name"

        // Criptografic configuration AES-256-GCM
        private const val AES_KEY_SIZE = 256
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128

        // Timeout for members out (30 secondi)
        private const val MEMBER_TIMEOUT_MS = 300_000L
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    // ==================== INITIALIZATION ====================

    init {
        // Create ID for this device if not exists
        if (!prefs.contains(KEY_DEVICE_ID)) {
            val deviceId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }

        // Set device name if not exists
        if (!prefs.contains(KEY_DEVICE_NAME)) {
            val deviceName = android.os.Build.MODEL
            prefs.edit().putString(KEY_DEVICE_NAME, deviceName).apply()
        }
    }

    // ==================== DEVICE INFO ====================

    fun getDeviceId(): String {
        return prefs.getString(KEY_DEVICE_ID, "") ?: ""
    }

    fun getDeviceName(): String {
        return prefs.getString(KEY_DEVICE_NAME, android.os.Build.MODEL) ?: android.os.Build.MODEL
    }

    fun setDeviceName(name: String) {
        prefs.edit().putString(KEY_DEVICE_NAME, name).apply()
    }

    // ==================== GROUP CREATION ====================

    /**
     * Crea a groups as HOST
     * @param groupName Group personalized name
     * @return Group created with criptografic key
     */
    fun createGroup(groupName: String): Group {
        val groupId = UUID.randomUUID().toString()
        val encryptionKey = generateEncryptionKey()
        val currentTime = System.currentTimeMillis()

        val group = Group(
            id = groupId,
            name = groupName,
            encryptionKey = encryptionKey,
            createdAt = currentTime,
            isHost = true,
            members = mutableListOf()
        )

        // Ad this device as first members
        val hostMember = GroupMember(
            deviceId = getDeviceId(),
            deviceName = getDeviceName(),
            joinedAt = currentTime,
            lastSeenAt = currentTime,
            isOnline = true
        )
        group.members.add(hostMember)

        // Save group
        saveGroup(group)
        setActiveGroup(group)

        Log.d(TAG, "Created new group: $groupName (ID: $groupId)")
        return group
    }

    /**
     * Join external group
     * @param encryptedData Group data received by bluetooth
     * @return add group to list
     */
    fun joinGroup(encryptedData: EncryptedGroupData): Group? {
        return try {
            // Decode
            val groupJson = decryptGroupData(encryptedData)
            val group = json.decodeFromString<Group>(groupJson)

            // Set this device as guest
            val guestGroup = group.copy(isHost = false)

            // Add this device as member
            val guestMember = GroupMember(
                deviceId = getDeviceId(),
                deviceName = getDeviceName(),
                joinedAt = System.currentTimeMillis(),
                isOnline = true
            )
            guestGroup.members.add(guestMember)

            // Save group
            saveGroup(guestGroup)
            setActiveGroup(guestGroup)

            Log.d(TAG, "Joined group: ${group.name} (ID: ${group.id})")
            guestGroup
        } catch (e: Exception) {
            Log.e(TAG, "Failed to join group: ${e.message}", e)
            null
        }
    }

    // ==================== GROUP ENCRYPTION ====================

    /**
     * Create
     */
    private fun generateEncryptionKey(): String {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(AES_KEY_SIZE, SecureRandom())
        val secretKey = keyGen.generateKey()
        return Base64.encodeToString(secretKey.encoded, Base64.NO_WRAP)
    }

    /**
     * Encripta un gruppo per trasmettere via Bluetooth
     * @param group Group
     * @return EncryptedGroupData ready for transmition BLE
     */
    fun encryptGroupForSharing(group: Group): EncryptedGroupData {
        val groupJson = json.encodeToString(group)
        val keyBytes = Base64.decode(group.encryptionKey, Base64.NO_WRAP)
        val secretKey = SecretKeySpec(keyBytes, "AES")

        // Genrate casual IV GCM
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)

        // Cript AES-256-GCM
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val encryptedBytes = cipher.doFinal(groupJson.toByteArray(Charsets.UTF_8))

        return EncryptedGroupData(
            groupId = group.id,
            encryptedPayload = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    /**
     * Decript
     */
    private fun decryptGroupData(encryptedData: EncryptedGroupData): String {

        // Decode data Base64
        val encryptedBytes = Base64.decode(encryptedData.encryptedPayload, Base64.NO_WRAP)
        val iv = Base64.decode(encryptedData.iv, Base64.NO_WRAP)

        throw NotImplementedError("Decryption requires ECDH key exchange - see BluetoothGroupSharing.kt")
    }

    // ==================== GROUP MANAGEMENT ====================

    /**
     * Save group in local memory
     */
    private fun saveGroup(group: Group) {
        val allGroups = getAllGroups().toMutableList()

        // Remove lat versione duplicata
        allGroups.removeAll { it.id == group.id }
        allGroups.add(group)

        val groupsJson = json.encodeToString(allGroups)
        prefs.edit().putString(KEY_ALL_GROUPS, groupsJson).apply()
    }

    fun getAllGroups(): List<Group> {
        val groupsJson = prefs.getString(KEY_ALL_GROUPS, "[]") ?: "[]"
        return try {
            json.decodeFromString<List<Group>>(groupsJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load groups: ${e.message}")
            emptyList()
        }
    }

    fun getActiveGroup(): Group? {
        val groupJson = prefs.getString(KEY_ACTIVE_GROUP, null) ?: return null
        return try {
            json.decodeFromString<Group>(groupJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load active group: ${e.message}")
            null
        }
    }

    fun setActiveGroup(group: Group?) {
        if (group == null) {
            prefs.edit().remove(KEY_ACTIVE_GROUP).apply()
        } else {
            val groupJson = json.encodeToString(group)
            prefs.edit().putString(KEY_ACTIVE_GROUP, groupJson).apply()
        }
    }

    fun deleteGroup(groupId: String) {
        val allGroups = getAllGroups().toMutableList()
        allGroups.removeAll { it.id == groupId }

        val groupsJson = json.encodeToString(allGroups)
        prefs.edit().putString(KEY_ALL_GROUPS, groupsJson).apply()

        val activeGroup = getActiveGroup()
        if (activeGroup?.id == groupId) {
            setActiveGroup(null)
        }

        Log.d(TAG, "Deleted group: $groupId")
    }

    fun leaveActiveGroup() {
        val group = getActiveGroup()
        if (group != null) {
            // remove this device from group
            group.members.removeAll { it.deviceId == getDeviceId() }

            if (group.isHost && group.members.isEmpty()) {
                deleteGroup(group.id)
            } else {
                setActiveGroup(null)
            }

            Log.d(TAG, "Left group: ${group.name}")
        }
    }

    // ==================== MEMBER MANAGEMENT ====================

    fun updateMember(member: GroupMember) {
        val group = getActiveGroup() ?: return

        val existingIndex = group.members.indexOfFirst { it.deviceId == member.deviceId }
        if (existingIndex >= 0) {
            group.members[existingIndex] = member
        } else {
            group.members.add(member)
        }

        saveGroup(group)
        setActiveGroup(group)
    }

    fun updateMemberPosition(deviceId: String, latitude: Double, longitude: Double) {
        val group = getActiveGroup() ?: return

        val member = group.members.find { it.deviceId == deviceId }
        if (member != null) {
            member.latitude = latitude
            member.longitude = longitude
            member.lastSeenAt = System.currentTimeMillis()
            member.isOnline = true

            saveGroup(group)
            setActiveGroup(group)
        }
    }

    fun checkMemberTimeouts() {
        val group = getActiveGroup() ?: return
        val currentTime = System.currentTimeMillis()
        var hasChanges = false

        group.members.forEach { member ->
            if (member.deviceId != getDeviceId()) {
                val timeSinceLastSeen = currentTime - member.lastSeenAt
                if (timeSinceLastSeen > MEMBER_TIMEOUT_MS && member.isOnline) {
                    member.isOnline = false
                    hasChanges = true
                    Log.d(TAG, "Member ${member.deviceName} marked as offline")
                }
            }
        }

        if (hasChanges) {
            saveGroup(group)
            setActiveGroup(group)
        }
    }

    fun removeMember(deviceId: String) {
        val group = getActiveGroup() ?: return
        group.members.removeAll { it.deviceId == deviceId }

        saveGroup(group)
        setActiveGroup(group)

        Log.d(TAG, "Removed member: $deviceId")
    }

    // ==================== ENCRYPTION UTILITIES ====================

    fun encryptMessage(message: String): EncryptedMessage? {
        val group = getActiveGroup() ?: return null

        try {
            val keyBytes = Base64.decode(group.encryptionKey, Base64.NO_WRAP)
            val secretKey = SecretKeySpec(keyBytes, "AES")

            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

            val encryptedBytes = cipher.doFinal(message.toByteArray(Charsets.UTF_8))

            return EncryptedMessage(
                payload = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP),
                iv = Base64.encodeToString(iv, Base64.NO_WRAP),
                groupId = group.id,
                deviceId = getDeviceId()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt message: ${e.message}", e)
            return null
        }
    }

    fun decryptMessage(encryptedMessage: EncryptedMessage): String? {
        val group = getActiveGroup() ?: return null

        if (encryptedMessage.groupId != group.id) {
            Log.w(TAG, "Message group ID mismatch")
            return null
        }

        try {
            val keyBytes = Base64.decode(group.encryptionKey, Base64.NO_WRAP)
            val secretKey = SecretKeySpec(keyBytes, "AES")

            val encryptedBytes = Base64.decode(encryptedMessage.payload, Base64.NO_WRAP)
            val iv = Base64.decode(encryptedMessage.iv, Base64.NO_WRAP)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt message: ${e.message}", e)
            return null
        }
    }

    // ==================== CLEANUP ====================

    fun clearAllGroups() {
        prefs.edit()
            .remove(KEY_ACTIVE_GROUP)
            .remove(KEY_ALL_GROUPS)
            .apply()

        Log.d(TAG, "Cleared all groups")
    }
}

// ==================== ENCRYPTED MESSAGE ====================

@Serializable
//@OptIn(InternalSerializationApi::class)
data class EncryptedMessage(
    val payload: String,               // Cripted datas (Base64)
    val iv: String,                    // Initialization Vector (Base64)
    val groupId: String,               // ID group
    val deviceId: String,              // ID sender
    val timestamp: Long = System.currentTimeMillis()
)