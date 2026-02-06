package com.example.earbud_tracker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews


class StatusWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences("CoreServicePrefs", Context.MODE_PRIVATE)
            val status = prefs.getString("widget_status", "STOPPED") ?: "STOPPED"
            val isRunning = status == "RUNNING"
            
            val statusText = if (isRunning) "Service: RUNNING" else "Service: STOPPED"
            val color = if (isRunning) 0xFF009900.toInt() else 0xFFFF0000.toInt() // Green or Red

            val views = RemoteViews(context.packageName, R.layout.widget_status)
            views.setTextViewText(R.id.app_widget_text, statusText)
            views.setInt(R.id.app_widget_text, "setBackgroundColor", color)

            // Click listener -> Launch ReviveActivity (works even if app is force-stopped)
            val intent = Intent(context, ReviveActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            
            val pendingIntent = PendingIntent.getActivity(
                context, 
                0, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.app_widget_text, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val widgetDetail = ComponentName(context, StatusWidgetProvider::class.java)
            val appWidgetIds = manager.getAppWidgetIds(widgetDetail)
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, manager, appWidgetId)
            }
        }
    }
}
