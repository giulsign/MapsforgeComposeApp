package it.fourSTL.PositionMarker

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
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

/**
 * GroupManager.kt
 * Gestisce la creazione, gestione e sicurezza dei gruppi per la condivisione posizione GPS
 */

// ==================== DATA CLASSES ====================

@Serializable
data class Group(
    val id: String,                    // UUID univoco del gruppo
    val name: String,                  // Nome personalizzato del gruppo
    val encryptionKey: String,         // Chiave AES-256 (Base64)
    val createdAt: Long,               // Timestamp creazione
    val isHost: Boolean,               // True se questo device è l'host
    var hostAddress: String = "",      // Indirizzo IP:porta dell'host
    val members: MutableList<GroupMember> = mutableListOf()
)

@Serializable
data class GroupMember(
    val deviceId: String,              // ID univoco del dispositivo
    val deviceName: String,            // Nome device (es. "Samsung Galaxy S21")
    val joinedAt: Long,                // Timestamp ingresso
    var lastSeenAt: Long = 0L,         // Ultimo aggiornamento posizione
    var latitude: Double = 0.0,        // Ultima latitudine nota
    var longitude: Double = 0.0,       // Ultima longitudine nota
    var isOnline: Boolean = false      // Stato connessione
)

@Serializable
data class EncryptedGroupData(
    val groupId: String,
    val encryptedPayload: String,      // Dati gruppo criptati
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

        // Configurazione crittografia AES-256-GCM
        private const val AES_KEY_SIZE = 256
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128

        // Timeout per considerare un membro offline (30 secondi)
        private const val MEMBER_TIMEOUT_MS = 30_000L
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    // ==================== INITIALIZATION ====================

    init {
        // Genera ID univoco per questo dispositivo se non esiste
        if (!prefs.contains(KEY_DEVICE_ID)) {
            val deviceId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }

        // Imposta nome device di default
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
     * Crea un nuovo gruppo come HOST
     * @param groupName Nome personalizzato del gruppo
     * @return Oggetto Group creato con chiave di crittografia
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
            isHost = true
        )

        // Aggiungi questo device come primo membro
        val hostMember = GroupMember(
            deviceId = getDeviceId(),
            deviceName = getDeviceName(),
            joinedAt = currentTime,
            lastSeenAt = currentTime,
            isOnline = true
        )
        group.members.add(hostMember)

        // Salva il gruppo
        saveGroup(group)
        setActiveGroup(group)

        Log.d(TAG, "Created new group: $groupName (ID: $groupId)")
        return group
    }

    /**
     * Unisciti a un gruppo esistente come GUEST
     * @param encryptedData Dati gruppo ricevuti via Bluetooth
     * @return Oggetto Group se decrittografia riuscita, null altrimenti
     */
    fun joinGroup(encryptedData: EncryptedGroupData): Group? {
        return try {
            // Decodifica i dati criptati
            val groupJson = decryptGroupData(encryptedData)
            val group = json.decodeFromString<Group>(groupJson)

            // Imposta questo device come guest
            val guestGroup = group.copy(isHost = false)

            // Aggiungi questo device come membro
            val guestMember = GroupMember(
                deviceId = getDeviceId(),
                deviceName = getDeviceName(),
                joinedAt = System.currentTimeMillis(),
                isOnline = true
            )
            guestGroup.members.add(guestMember)

            // Salva il gruppo
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
     * Genera una chiave di crittografia AES-256 casuale
     */
    private fun generateEncryptionKey(): String {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(AES_KEY_SIZE, SecureRandom())
        val secretKey = keyGen.generateKey()
        return Base64.encodeToString(secretKey.encoded, Base64.NO_WRAP)
    }

    /**
     * Cripta i dati del gruppo per condivisione via Bluetooth
     * @param group Gruppo da crittare
     * @return EncryptedGroupData pronto per trasmissione BLE
     */
    fun encryptGroupForSharing(group: Group): EncryptedGroupData {
        val groupJson = json.encodeToString(group)
        val keyBytes = Base64.decode(group.encryptionKey, Base64.NO_WRAP)
        val secretKey = SecretKeySpec(keyBytes, "AES")

        // Genera IV casuale per GCM
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)

        // Cripta con AES-256-GCM
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
     * Decripta i dati del gruppo ricevuti via Bluetooth
     */
    private fun decryptGroupData(encryptedData: EncryptedGroupData): String {
        // Per semplicità, assumiamo che la chiave sia inclusa nell'encrypted payload
        // In un'implementazione reale, useremmo ECDH per scambio chiavi sicuro

        // Decodifica i dati Base64
        val encryptedBytes = Base64.decode(encryptedData.encryptedPayload, Base64.NO_WRAP)
        val iv = Base64.decode(encryptedData.iv, Base64.NO_WRAP)

        // NOTA: In produzione, qui implementeremmo:
        // 1. Handshake ECDH via Bluetooth per scambio chiavi
        // 2. Derivazione chiave condivisa con HKDF
        // Per ora usiamo un approccio semplificato

        // Questa è una semplificazione: la chiave dovrebbe essere scambiata in modo sicuro
        // via ECDH prima della trasmissione dei dati
        throw NotImplementedError("Decryption requires ECDH key exchange - see BluetoothGroupSharing.kt")
    }

    // ==================== GROUP MANAGEMENT ====================

    /**
     * Salva un gruppo in memoria locale
     */
    private fun saveGroup(group: Group) {
        val allGroups = getAllGroups().toMutableList()

        // Rimuovi versione precedente se esiste
        allGroups.removeAll { it.id == group.id }
        allGroups.add(group)

        val groupsJson = json.encodeToString(allGroups)
        prefs.edit().putString(KEY_ALL_GROUPS, groupsJson).apply()
    }

    /**
     * Ottieni tutti i gruppi salvati
     */
    fun getAllGroups(): List<Group> {
        val groupsJson = prefs.getString(KEY_ALL_GROUPS, "[]") ?: "[]"
        return try {
            json.decodeFromString<List<Group>>(groupsJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load groups: ${e.message}")
            emptyList()
        }
    }

    /**
     * Ottieni il gruppo attivo corrente
     */
    fun getActiveGroup(): Group? {
        val groupJson = prefs.getString(KEY_ACTIVE_GROUP, null) ?: return null
        return try {
            json.decodeFromString<Group>(groupJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load active group: ${e.message}")
            null
        }
    }

    /**
     * Imposta il gruppo attivo
     */
    fun setActiveGroup(group: Group?) {
        if (group == null) {
            prefs.edit().remove(KEY_ACTIVE_GROUP).apply()
        } else {
            val groupJson = json.encodeToString(group)
            prefs.edit().putString(KEY_ACTIVE_GROUP, groupJson).apply()
        }
    }

    /**
     * Elimina un gruppo
     */
    fun deleteGroup(groupId: String) {
        val allGroups = getAllGroups().toMutableList()
        allGroups.removeAll { it.id == groupId }

        val groupsJson = json.encodeToString(allGroups)
        prefs.edit().putString(KEY_ALL_GROUPS, groupsJson).apply()

        // Se era il gruppo attivo, rimuovilo
        val activeGroup = getActiveGroup()
        if (activeGroup?.id == groupId) {
            setActiveGroup(null)
        }

        Log.d(TAG, "Deleted group: $groupId")
    }

    /**
     * Abbandona il gruppo attivo
     */
    fun leaveActiveGroup() {
        val group = getActiveGroup()
        if (group != null) {
            // Rimuovi questo device dai membri
            group.members.removeAll { it.deviceId == getDeviceId() }

            if (group.isHost && group.members.isEmpty()) {
                // Se ero l'host e non ci sono altri membri, elimina il gruppo
                deleteGroup(group.id)
            } else {
                // Altrimenti solo disattiva il gruppo
                setActiveGroup(null)
            }

            Log.d(TAG, "Left group: ${group.name}")
        }
    }

    // ==================== MEMBER MANAGEMENT ====================

    /**
     * Aggiungi o aggiorna un membro nel gruppo attivo
     */
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

    /**
     * Aggiorna la posizione di un membro
     */
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

    /**
     * Segna i membri come offline se non inviano aggiornamenti da troppo tempo
     */
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

    /**
     * Rimuovi un membro dal gruppo
     */
    fun removeMember(deviceId: String) {
        val group = getActiveGroup() ?: return
        group.members.removeAll { it.deviceId == deviceId }

        saveGroup(group)
        setActiveGroup(group)

        Log.d(TAG, "Removed member: $deviceId")
    }

    // ==================== ENCRYPTION UTILITIES ====================

    /**
     * Cripta un messaggio con la chiave del gruppo attivo
     * Usato per crittare coordinate GPS prima dell'invio via WebSocket
     */
    fun encryptMessage(message: String): EncryptedMessage? {
        val group = getActiveGroup() ?: return null

        try {
            val keyBytes = Base64.decode(group.encryptionKey, Base64.NO_WRAP)
            val secretKey = SecretKeySpec(keyBytes, "AES")

            // Genera IV casuale
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)

            // Cripta con AES-256-GCM
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

    /**
     * Decripta un messaggio con la chiave del gruppo attivo
     */
    fun decryptMessage(encryptedMessage: EncryptedMessage): String? {
        val group = getActiveGroup() ?: return null

        // Verifica che il messaggio appartenga a questo gruppo
        if (encryptedMessage.groupId != group.id) {
            Log.w(TAG, "Message group ID mismatch")
            return null
        }

        try {
            val keyBytes = Base64.decode(group.encryptionKey, Base64.NO_WRAP)
            val secretKey = SecretKeySpec(keyBytes, "AES")

            val encryptedBytes = Base64.decode(encryptedMessage.payload, Base64.NO_WRAP)
            val iv = Base64.decode(encryptedMessage.iv, Base64.NO_WRAP)

            // Decripta con AES-256-GCM
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

    /**
     * Pulisci tutti i dati dei gruppi
     */
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
data class EncryptedMessage(
    val payload: String,               // Dati criptati (Base64)
    val iv: String,                    // Initialization Vector (Base64)
    val groupId: String,               // ID del gruppo (per validazione)
    val deviceId: String,              // ID del mittente
    val timestamp: Long = System.currentTimeMillis()
)