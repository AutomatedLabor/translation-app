package com.translator.screen.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.translator.screen.R
import com.translator.screen.service.TranslatorAccessibilityService
import com.translator.screen.ui.MainActivity

/**
 * Home-screen widget (Phase 6). A 1×1 "Translate" button that toggles live translation
 * when the accessibility service is connected, or opens the setup screen ([MainActivity])
 * when it is not (so a first-time user is guided to enable accessibility + download the
 * model rather than the widget silently doing nothing).
 *
 * The widget is in the app's single process, so [TranslatorAccessibilityService.instance]
 * is directly reachable. [toggleContinuousMode] runs on the main thread (AppWidgetProvider
 * callbacks are main-thread), which is what the overlay expects.
 *
 * Tap → broadcast [ACTION_TOGGLE] to this provider (FLAG_IMMUTABLE required on API 31+).
 */
class TranslateWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_translate)
            val toggleIntent = Intent(context, TranslateWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE
            }
            val pi = PendingIntent.getBroadcast(
                context,
                id,
                toggleIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
            val service = TranslatorAccessibilityService.instance
            if (service != null && TranslatorAccessibilityService.isReady()) {
                Log.i(TAG, "Widget toggle: service ready, toggling continuous mode")
                service.toggleContinuousMode()
            } else {
                Log.i(TAG, "Widget toggle: service not ready, opening setup")
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(launchIntent)
            }
        }
    }

    companion object {
        private const val TAG = "TranslateWidgetProvider"
        private const val ACTION_TOGGLE = "com.translator.screen.WIDGET_TOGGLE"
    }
}
