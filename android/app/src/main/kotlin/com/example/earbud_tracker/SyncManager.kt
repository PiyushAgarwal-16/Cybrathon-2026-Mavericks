package com.example.earbud_tracker

import android.content.Context
import android.util.Log
import com.example.earbud_tracker.database.AppDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

object SyncManager {
    private const val TAG = "FirestoreSync"
    private val isSyncInProgress = AtomicBoolean(false)

    suspend fun triggerSync(context: Context, source: String): Int {
        if (isSyncInProgress.get()) {
            Log.d(TAG, "SYNC_SKIPPED: Sync already in progress (Source: $source)")
            return 0
        }

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.d(TAG, "SYNC_SKIPPED: User not logged in (Source: $source)")
            return 0
        }

        isSyncInProgress.set(true)
        Log.d(TAG, "SYNC_TRIGGER: $source")

        var successCount = 0
        try {
            // Ensure we are off the main thread for DB and Network
            successCount = withContext(Dispatchers.IO) {
                var count = 0
                try {
                    val db = FirebaseFirestore.getInstance()
                    val dao = AppDatabase.getDatabase(context).listeningSessionDao()
                    val unsyncedSessions = dao.getUnsyncedSessions()
                    
                    if (unsyncedSessions.isEmpty()) {
                        Log.d(TAG, "SYNC_COMPLETE: No unsynced sessions found.")
                    }

                    for (session in unsyncedSessions) {
                        try {
                            val sessionData = hashMapOf(
                                "id" to session.id,
                                "deviceName" to session.deviceName,
                                "startTime" to session.startTime,
                                "endTime" to session.endTime,
                                "durationSeconds" to session.durationSeconds,
                                "avgVolume" to session.avgVolume,
                                "maxVolume" to session.maxVolume,
                                "createdAt" to session.createdAt
                            )

                            val docRef = db.collection("users")
                                .document(user.uid)
                                .collection("sessions")
                                .document(session.id)

                            // Synchronous wait for Firestore write
                            Tasks.await(docRef.set(sessionData))
                            
                            // Mark as synced locally
                            dao.markSessionAsSynced(session.id)
                            Log.d(TAG, "SYNCED_SESSION: ${session.id}")
                            count++
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to sync session ${session.id}", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Critical Sync Error inside IO block", e)
                }
                count
            }
        } catch (e: Exception) {
            Log.e(TAG, "SYNC_ERROR", e)
        } finally {
            isSyncInProgress.set(false)
        }
        
        return successCount
    }
}
