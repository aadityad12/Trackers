package com.example.apextracker

import android.content.Context
import android.provider.Settings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseManager(private val context: Context) {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    
    val currentUser: FirebaseUser? get() = auth.currentUser
    val userId: String? get() = auth.currentUser?.uid
    
    val deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }

    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { 
            trySend(it.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun syncSettings(theme: String, isDarkMode: Boolean) {
        val uid = userId ?: return
        val data = mapOf(
            "theme" to theme,
            "isDarkMode" to isDarkMode,
            "lastSynced" to System.currentTimeMillis()
        )
        firestore.collection("users").document(uid)
            .set(data, SetOptions.merge())
            .await()
    }

    fun getSettingsFlow(): Flow<Map<String, Any>?> = callbackFlow {
        val uid = userId ?: return@callbackFlow
        val listener = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.data)
            }
        awaitClose { listener.remove() }
    }

    // Screen Time Aggregation
    suspend fun uploadScreenTime(millis: Long) {
        val uid = userId ?: return
        val data = mapOf(
            "durationMillis" to millis,
            "timestamp" to System.currentTimeMillis(),
            "deviceName" to android.os.Build.MODEL,
            "deviceId" to deviceId
        )
        firestore.collection("users").document(uid)
            .collection("devices").document(deviceId)
            .set(mapOf("lastUsage" to data), SetOptions.merge())
            .await()
    }

    fun getAggregatedScreenTime(): Flow<List<DeviceUsage>> = callbackFlow {
        val uid = userId ?: return@callbackFlow
        val listener = firestore.collection("users").document(uid)
            .collection("devices")
            .addSnapshotListener { snapshot, _ ->
                val devices = snapshot?.documents?.mapNotNull { doc ->
                    val usage = doc.get("lastUsage") as? Map<String, Any>
                    if (usage != null) {
                        DeviceUsage(
                            deviceName = usage["deviceName"] as? String ?: "Unknown Device",
                            durationMillis = usage["durationMillis"] as? Long ?: 0L,
                            deviceId = usage["deviceId"] as? String ?: ""
                        )
                    } else null
                } ?: emptyList()
                trySend(devices)
            }
        awaitClose { listener.remove() }
    }
}

data class DeviceUsage(
    val deviceName: String,
    val durationMillis: Long,
    val deviceId: String
)
