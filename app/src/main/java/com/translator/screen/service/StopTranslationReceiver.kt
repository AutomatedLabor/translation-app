package com.translator.screen.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Handles the "Stop" action button on the continuous-mode notification (Phase 6).
 *
 * The notification is posted by [TranslatorAccessibilityService.showContinuousNotification]
 * with an action whose [PendingIntent] targets this receiver. Tapping "Stop" in the
 * shade stops live translation + dismisses the overlay without the user having to pull
 * the shade down to the tile.
 *
 * Registered in the manifest (not exported) with intent action
 * [ACTION_STOP_TRANSLATION]. The PendingIntent is created with FLAG_IMMUTABLE (required
 * on API 31+, safe on older versions).
 */
class StopTranslationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STOP_TRANSLATION) return
        Log.i(TAG, "Stop action received")
        TranslatorAccessibilityService.instance?.stopContinuousMode()
    }

    companion object {
        private const val TAG = "StopTranslationReceiver"
        const val ACTION_STOP_TRANSLATION = "com.translator.screen.STOP_TRANSLATION"
    }
}
