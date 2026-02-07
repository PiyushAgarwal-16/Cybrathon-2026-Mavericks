package com.example.earbud_tracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import android.content.pm.ServiceInfo
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.Manifest
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothProfile

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import com.example.earbud_tracker.database.AppDatabase
import com.example.earbud_tracker.database.ListeningSessionEntity

class CoreService : Service() {
    companion object {
        private const val CHANNEL_ID = "core_service"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "CoreService"
        private const val PREFS_NAME = "CoreServicePrefs"
        private const val KEY_LAST_HEARTBEAT = "last_heartbeat"
        private const val KEY_WIDGET_STATUS = "widget_status"
        
        var isServiceRunning = false

        fun isServiceAlive(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastHeartbeat = prefs.getLong(KEY_LAST_HEARTBEAT, 0L)
            val now = System.currentTimeMillis()
            // If heartbeat is older than 20 seconds, it's considered dead
            return (now - lastHeartbeat) < 20000
        }

        private fun updateWidgetState(context: Context, status: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_WIDGET_STATUS, status).apply()
            StatusWidgetProvider.updateAllWidgets(context)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var audioManager: AudioManager

    // Session State
    private var bluetoothConnected = false
    private var connectedDeviceName: String? = null
    private var audioPlaying = false
    private var isSessionActive = false
    private var isCallSession = false

    // Session Stats
    private var sessionStartTime: Long = 0
    private var sessionVolumeSum: Long = 0
    private var sessionVolumeSamples: Int = 0
    private var sessionMaxVolume: Int = 0
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            updateHeartbeat()
            // Schedule next heartbeat in 10 seconds
            handler.postDelayed(this, 10000)
        }
    }

    private val audioCheckRunnable = object : Runnable {
        override fun run() {
            checkAudioState()
            if (isSessionActive) {
                sampleVolume()
            }
            handler.postDelayed(this, 2000)
        }
    }

    private val notificationCheckRunnable = object : Runnable {
        override fun run() {
            if (isServiceRunning) {
                Log.d(TAG, "Periodic notification check")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID, 
                        createNotification(true),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, createNotification(true))
                }
            }
            handler.postDelayed(this, 15000)
        }
    }

    private val earbudReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", -1)
                    if (state == 1) {
                        Log.d(TAG, "WIRED_CONNECTED")
                    } else if (state == 0) {
                        Log.d(TAG, "WIRED_DISCONNECTED")
                    }
                }
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    
                    val deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && 
                        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        "Unknown (No Permission)"
                    } else {
                        device?.name ?: device?.address ?: "Unknown"
                    }

                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        Log.d(TAG, "BT_CONNECTED: $deviceName")
                        bluetoothConnected = true
                        connectedDeviceName = deviceName
                        checkAudioState() 
                    } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.d(TAG, "BT_DISCONNECTED: $deviceName")
                        bluetoothConnected = false
                        connectedDeviceName = null
                        checkAudioState()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        Log.d(TAG, "onCreate: Initializing service")
        
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                createNotification(true),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification(true))
        }
        
        // Initial State Hydration
        try {
            val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            if (adapter != null && 
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || 
                 checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)) {
                
                val a2dpState = adapter.getProfileConnectionState(BluetoothProfile.A2DP)
                val headsetState = adapter.getProfileConnectionState(BluetoothProfile.HEADSET)
                
                if (a2dpState == BluetoothProfile.STATE_CONNECTED || headsetState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "Initial State: BT Connected")
                    bluetoothConnected = true
                    connectedDeviceName = "Unknown (Initial)"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking initial BT state", e)
        }

        val mode = audioManager.mode
        val isCallActive = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
        if (audioManager.isMusicActive() || isCallActive) {
            audioPlaying = true
            Log.d(TAG, "Initial State: Audio Playing (Music: ${audioManager.isMusicActive()}, Call: $isCallActive)")
        }

        if (bluetoothConnected && audioPlaying) {
             Log.d(TAG, "SESSION_START_INITIAL_STATE: $connectedDeviceName")
             startSession(isCallActive)
        }
        
        updateWidgetState(this, "RUNNING")
        AlarmReceiver.schedule(this)

        // Register EarbudReceiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        }
        registerReceiver(earbudReceiver, filter)
        
        // Start heartbeat loop
        handler.post(heartbeatRunnable)
        // Start audio check loop
        handler.post(audioCheckRunnable)
        // Start notification check loop
        handler.post(notificationCheckRunnable)
        
        // Trigger Sync on App Start
        CoroutineScope(Dispatchers.IO).launch {
            SyncManager.triggerSync(applicationContext, "APP_START")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: Service running")
        // Ensuring foreground is active
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                createNotification(true),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification(true))
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "onTaskRemoved: Task removed, restarting foreground notification")
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                createNotification(true),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification(true))
        }
        Log.d(TAG, "NOTIFICATION_RESTORED")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: Service stopping")
        
        if (isSessionActive) {
             Log.d(TAG, "SESSION_ABORTED_SERVICE_RESTART")
             isSessionActive = false
        }

        isServiceRunning = false
        updateWidgetState(this, "STOPPED")
        // Stop heartbeat
        handler.removeCallbacks(heartbeatRunnable)
        handler.removeCallbacks(audioCheckRunnable)
        handler.removeCallbacks(notificationCheckRunnable)
        
        try {
            unregisterReceiver(earbudReceiver)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Error unregistering receiver: ${e.message}")
        }
        
        super.onDestroy()
    }

    private fun checkAudioState() {
        val isMusicActive = audioManager.isMusicActive()
        val mode = audioManager.mode
        val isCallActive = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
        val isAudioActive = isMusicActive || isCallActive
        
        if (isAudioActive != audioPlaying) {
            audioPlaying = isAudioActive
        }

        if (bluetoothConnected && audioPlaying) {
            if (!isSessionActive) {
                startSession(isCallActive)
            }
        } else {
            if (isSessionActive) {
                endSession()
            }
        }
    }

    private fun startSession(isCall: Boolean = false) {
        isSessionActive = true
        isCallSession = isCall
        sessionStartTime = System.currentTimeMillis()
        sessionVolumeSum = 0
        sessionVolumeSamples = 0
        sessionMaxVolume = 0
        
        // Initial sample
        sampleVolume()
        
        if (isCallSession) {
            Log.d(TAG, "SESSION_START_CALL: ${connectedDeviceName ?: "Unknown"}")
        } else {
            Log.d(TAG, "SESSION_START: ${connectedDeviceName ?: "Unknown"}")
        }
    }

    private fun endSession() {
        if (!isSessionActive) return
        
        val now = System.currentTimeMillis()
        val durationSeconds = (now - sessionStartTime) / 1000
        val avgVolume = if (sessionVolumeSamples > 0) (sessionVolumeSum / sessionVolumeSamples).toInt() else 0
        
        if (isCallSession) {
            Log.d(TAG, "SESSION_END_CALL: ${connectedDeviceName ?: "Unknown"} ${durationSeconds}s AVG_VOL=$avgVolume MAX_VOL=$sessionMaxVolume")
        } else {
            Log.d(TAG, "SESSION_END: ${connectedDeviceName ?: "Unknown"} ${durationSeconds}s AVG_VOL=$avgVolume MAX_VOL=$sessionMaxVolume")
        }
        
        // Persist to Database
        val sessionId = UUID.randomUUID().toString()
        val sessionEntity = ListeningSessionEntity(
            id = sessionId,
            deviceName = connectedDeviceName ?: "Unknown",
            startTime = sessionStartTime,
            endTime = now,
            durationSeconds = durationSeconds,
            avgVolume = avgVolume,
            maxVolume = sessionMaxVolume,
            updatedAt = now
        )
        
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getDatabase(applicationContext).listeningSessionDao().insertSession(sessionEntity)
             Log.d(TAG, "SESSION_SAVED: $sessionId")
             logStatistics()
             
             // Trigger Sync for New Session
             SyncManager.triggerSync(applicationContext, "NEW_SESSION")
        }
    }

    private fun logStatistics() {
        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.getDatabase(applicationContext).listeningSessionDao()
            
            // Today's Total
            val calendar = java.util.Calendar.getInstance()
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            val startOfDay = calendar.timeInMillis
            val endOfDay = startOfDay + 86400000 // 24 * 60 * 60 * 1000
            
            val todaySeconds = dao.getTotalDurationInRange(startOfDay, endOfDay) ?: 0
            Log.d(TAG, "TODAY_TOTAL: $todaySeconds")
            
            // Daily Summaries
            val summaries = dao.getDailySummaries()
            summaries.forEach { summary ->
                Log.d(TAG, "DAY_SUMMARY: ${summary.date} ${summary.totalDurationSeconds} AVG_VOL=${summary.avgVolume} MAX_VOL=${summary.maxVolume}")
            }
        }

        isSessionActive = false
    }

    private fun sampleVolume() {
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        sessionVolumeSum += currentVol
        sessionVolumeSamples++
        if (currentVol > sessionMaxVolume) {
           sessionMaxVolume = currentVol
        }
    }

    private fun updateHeartbeat() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_HEARTBEAT, System.currentTimeMillis()).apply()
        // Update widgets on heartbeat
        updateWidgetState(this, "RUNNING")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Core Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(isAlive: Boolean): Notification {
        val statusText = if (isAlive) "🟢 Earbud Tracker running" else "🔴 Earbud Tracker stopped"
        
        // Create Restart Action
        val restartIntent = Intent(this, RestartReceiver::class.java)
        val restartPendingIntent = PendingIntent.getBroadcast(
            this, 
            0, 
            restartIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Earbud Tracker")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_rotate, "Restart Service", restartPendingIntent)
            .build()
    }
}
