package com.example.earbud_tracker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class WatchdogAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval = 15000L // 15 seconds
    private val TAG = "WatchdogService"

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            checkAndRestartService()
            handler.postDelayed(this, checkInterval)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "onServiceConnected: Watchdog started")
        handler.post(watchdogRunnable)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No action needed for events, just running to keep process alive/watchdog active
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt: Watchdog interrupted")
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: Watchdog stopping")
        handler.removeCallbacks(watchdogRunnable)
        super.onDestroy()
    }

    private fun checkAndRestartService() {
        // Use the heartbeat check to determine if service is alive
        if (!CoreService.isServiceAlive(this)) {
            Log.d(TAG, "checkAndRestartService: CoreService dead (stale heartbeat), restarting...")
            val intent = Intent(this, CoreService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } else {
            // Log.d(TAG, "checkAndRestartService: CoreService is running")
        }
    }
}
