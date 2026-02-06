package com.example.earbud_tracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import io.flutter.embedding.android.FlutterActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.google.firebase.FirebaseApp





class MainActivity : FlutterActivity() {

        override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                 Log.d("MainActivity", "Requesting BLUETOOTH_CONNECT permission")
                 ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 102)
            } else {
                 Log.d("MainActivity", "BLUETOOTH_CONNECT permission already granted")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "Requesting POST_NOTIFICATIONS permission")
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            } else {
                 Log.d("MainActivity", "POST_NOTIFICATIONS permission already granted")
            }
        }

        val intent = Intent(this, CoreService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }


    override fun configureFlutterEngine(flutterEngine: io.flutter.embedding.engine.FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        io.flutter.plugin.common.MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "earbud_tracker/dashboard")
            .setMethodCallHandler { call, result ->
                if (call.method == "getTodaySummary") {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val dao = com.example.earbud_tracker.database.AppDatabase.getDatabase(applicationContext).listeningSessionDao()
                            
                            val calendar = java.util.Calendar.getInstance()
                            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                            calendar.set(java.util.Calendar.MINUTE, 0)
                            calendar.set(java.util.Calendar.SECOND, 0)
                            calendar.set(java.util.Calendar.MILLISECOND, 0)
                            val startOfDay = calendar.timeInMillis
                            val endOfDay = startOfDay + 86400000 
                            
                            val sessions = dao.getSessionsByDay(startOfDay, endOfDay)
                            
                            var totalDuration = 0L
                            var totalAvgVol = 0L
                            var maxVol = 0
                            val count = sessions.size
                            
                            for (s in sessions) {
                                totalDuration += s.durationSeconds
                                totalAvgVol += s.avgVolume
                                if (s.maxVolume > maxVol) maxVol = s.maxVolume
                            }
                            
                            val avgVol = if (count > 0) (totalAvgVol / count).toInt() else 0
                            
                            val data = mapOf(
                                "totalDurationSeconds" to totalDuration,
                                "avgVolume" to avgVol,
                                "maxVolume" to maxVol,
                                "sessionCount" to count
                            )
                            
                            withContext(Dispatchers.Main) {
                                result.success(data)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                result.error("DB_ERROR", e.message, null)
                            }
                        }
                    }
                } else if (call.method == "getSessionsForDay") {
                    val dateStr = call.argument<String>("date")
                    if (dateStr == null) {
                        result.error("INVALID_ARGUMENT", "Date is required", null)
                        return@setMethodCallHandler
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val dao = com.example.earbud_tracker.database.AppDatabase.getDatabase(applicationContext).listeningSessionDao()
                            
                            val parts = dateStr.split("-")
                            if (parts.size != 3) {
                                withContext(Dispatchers.Main) {
                                    result.error("INVALID_FORMAT", "Date must be yyyy-MM-dd", null)
                                }
                                return@launch
                            }

                            val calendar = java.util.Calendar.getInstance()
                            calendar.set(java.util.Calendar.YEAR, parts[0].toInt())
                            calendar.set(java.util.Calendar.MONTH, parts[1].toInt() - 1) // Calendar months are 0-indexed
                            calendar.set(java.util.Calendar.DAY_OF_MONTH, parts[2].toInt())
                            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                            calendar.set(java.util.Calendar.MINUTE, 0)
                            calendar.set(java.util.Calendar.SECOND, 0)
                            calendar.set(java.util.Calendar.MILLISECOND, 0)
                            
                            val startOfDay = calendar.timeInMillis
                            val endOfDay = startOfDay + 86400000 

                            val sessions = dao.getSessionsByDay(startOfDay, endOfDay)
                            
                            val data = sessions.map { s ->
                                mapOf(
                                    "startTime" to s.startTime,
                                    "endTime" to s.endTime,
                                    "durationSeconds" to s.durationSeconds,
                                    "avgVolume" to s.avgVolume,
                                    "maxVolume" to s.maxVolume,
                                    "deviceName" to s.deviceName
                                )
                            }
                            
                            withContext(Dispatchers.Main) {
                                result.success(data)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                result.error("DB_ERROR", e.message, null)
                            }
                        }
                    }
                } else if (call.method == "getDailySummaries") {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val dao = com.example.earbud_tracker.database.AppDatabase.getDatabase(applicationContext).listeningSessionDao()
                            val summaries = dao.getDailySummaries()
                            
                            val data = summaries.map { s ->
                                mapOf(
                                    "date" to s.date,
                                    "totalDurationSeconds" to s.totalDurationSeconds,
                                    "avgVolume" to s.avgVolume,
                                    "maxVolume" to s.maxVolume
                                )
                            }
                            
                            withContext(Dispatchers.Main) {
                                result.success(data)
                            }
                        } catch (e: Exception) {
                             withContext(Dispatchers.Main) {
                                result.error("DB_ERROR", e.message, null)
                            }
                        }
                    }
                } else if (call.method == "getLast7DaysSummary") {
                     CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val dao = com.example.earbud_tracker.database.AppDatabase.getDatabase(applicationContext).listeningSessionDao()
                            val summaries = dao.getDailySummaries()
                            
                            // Take last 7 days and reverse to get Oldest -> Newest
                            val recent7 = summaries.take(7).reversed()
                            
                            val data = recent7.map { s ->
                                mapOf(
                                    "date" to s.date,
                                    "totalDurationSeconds" to s.totalDurationSeconds,
                                    "avgVolume" to s.avgVolume,
                                    "maxVolume" to s.maxVolume
                                )
                            }
                            
                            withContext(Dispatchers.Main) {
                                result.success(data)
                            }
                        } catch (e: Exception) {
                             withContext(Dispatchers.Main) {
                                result.error("DB_ERROR", e.message, null)
                            }
                        }
                    }
                } else if (call.method == "triggerSync") {
                    CoroutineScope(Dispatchers.IO).launch {
                         try {
                            // Delegate to SyncManager
                            val count = SyncManager.triggerSync(applicationContext, "MANUAL_LOGIN")
                            
                            withContext(Dispatchers.Main) {
                                result.success(count)
                            }
                         } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                result.error("SYNC_ERROR", e.message, null)
                            }
                         }
                    }
                } else {
                    result.notImplemented()
                }
            }
    }
}
