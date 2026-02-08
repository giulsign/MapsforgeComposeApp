package it.fourSTL.PositionMarker

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

object FirebaseConfig {

    private var database: FirebaseDatabase? = null

    fun initialize(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        if (database == null) {
            database = FirebaseDatabase.getInstance().apply {
                setPersistenceEnabled(true)
            }
        }
    }

    fun getDatabase(): FirebaseDatabase {
        return database ?: throw IllegalStateException(
            "Firebase not initialized. Call FirebaseConfig.initialize(context) in your application or activity.."
        )
    }

    fun getDatabaseReference(path: String): DatabaseReference = getDatabase().getReference(path)
}