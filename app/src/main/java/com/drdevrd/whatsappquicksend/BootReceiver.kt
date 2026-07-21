package com.drdevrd.whatsappquicksend

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Placeholder receiver for BOOT_COMPLETED.
 * NOTE: Scheduled reminders set with AlarmManager are cleared when the phone
 * reboots. This app does not currently persist scheduled times to re-arm
 * them on boot — if that's needed later, save the phone/message/time to
 * SharedPreferences when scheduling, and re-schedule here.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // No-op for now.
    }
}
