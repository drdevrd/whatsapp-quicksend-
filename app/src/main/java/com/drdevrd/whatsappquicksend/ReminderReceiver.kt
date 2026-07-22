package com.drdevrd.whatsappquicksend

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(MainActivity.EXTRA_ID, 0)
        val name = intent.getStringExtra(MainActivity.EXTRA_NAME) ?: ""
        val phone = intent.getStringExtra(MainActivity.EXTRA_PHONE) ?: ""
        val message = intent.getStringExtra(MainActivity.EXTRA_MSG) ?: ""
        val recurrence = intent.getIntExtra(MainActivity.EXTRA_RECURRENCE, 0)

        if (recurrence != 0) {
            // Repeating reminder: arm the next occurrence and update the stored entry's time.
            rescheduleNext(context, id, name, phone, message, recurrence)
        } else {
            // One-time reminder: it has fired, remove it from the visible list.
            ReminderStore.remove(context, id)
        }

        showNotification(context, id, name, phone, message)
    }

    private fun rescheduleNext(
        context: Context, id: Int, name: String, phone: String, message: String, recurrence: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val next = Calendar.getInstance().apply {
            when (recurrence) {
                1 -> add(Calendar.DAY_OF_YEAR, 1)
                2 -> add(Calendar.WEEK_OF_YEAR, 1)
            }
        }
        val nextIntent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(MainActivity.EXTRA_ID, id)
            putExtra(MainActivity.EXTRA_NAME, name)
            putExtra(MainActivity.EXTRA_PHONE, phone)
            putExtra(MainActivity.EXTRA_MSG, message)
            putExtra(MainActivity.EXTRA_RECURRENCE, recurrence)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, id, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pendingIntent)
            }
            // Update the stored entry so the on-screen list shows the next occurrence time.
            ReminderStore.remove(context, id)
            ReminderStore.add(context, ReminderEntry(id, name, phone, message, next.timeInMillis, recurrence))
        } catch (e: SecurityException) {
            // Exact alarm permission revoked; silently skip re-arming.
        }
    }

    private fun showNotification(context: Context, id: Int, name: String, phone: String, message: String) {
        val channelId = "wa_quick_send_reminders"
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "WhatsApp Reminders", NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_NAME, name)
            putExtra(MainActivity.EXTRA_PHONE, phone)
            putExtra(MainActivity.EXTRA_MSG, message)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, id, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, SnoozeReceiver::class.java).apply {
            putExtra(MainActivity.EXTRA_ID, id + SNOOZE_ID_OFFSET)
            putExtra(MainActivity.EXTRA_NAME, name)
            putExtra(MainActivity.EXTRA_PHONE, phone)
            putExtra(MainActivity.EXTRA_MSG, message)
            putExtra("notif_id", id)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context, id + SNOOZE_ID_OFFSET, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (name.isNotBlank()) "WhatsApp reminder — $name" else "WhatsApp reminder"

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText("Tap to review and send your message")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_popup_reminder, "Snooze 10 min", snoozePendingIntent)
            .build()

        notificationManager.notify(id, notification)
    }

    companion object {
        // Offsets keep snooze/notification pending-intent request codes from colliding
        // with the main reminder's own id.
        const val SNOOZE_ID_OFFSET = 500000
    }
}
