package com.drdevrd.whatsappquicksend

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast

class SnoozeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val phone = intent.getStringExtra(MainActivity.EXTRA_PHONE) ?: ""
        val message = intent.getStringExtra(MainActivity.EXTRA_MSG) ?: ""

        // Dismiss the current notification
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(MainActivity.REQUEST_CODE)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + 10 * 60 * 1000 // 10 minutes

        // Snoozed occurrence never carries recurrence forward — the next
        // regular occurrence was already armed by ReminderReceiver if needed.
        val snoozedIntent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(MainActivity.EXTRA_PHONE, phone)
            putExtra(MainActivity.EXTRA_MSG, message)
            putExtra(MainActivity.EXTRA_RECURRENCE, 0)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, MainActivity.REQUEST_CODE + 2, snoozedIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
            Toast.makeText(context, "Snoozed for 10 minutes", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(context, "Could not snooze: permission denied", Toast.LENGTH_SHORT).show()
        }
    }
}
