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
        val phone = intent.getStringExtra(MainActivity.EXTRA_PHONE) ?: ""
        val message = intent.getStringExtra(MainActivity.EXTRA_MSG) ?: ""
        val recurrence = intent.getIntExtra(MainActivity.EXTRA_RECURRENCE, 0)

        // If this reminder repeats, arm the NEXT occurrence now, before showing
        // this notification, so it's not lost even if the phone is put away.
        if (recurrence != 0) {
            rescheduleNext(context, phone, message, recurrence)
        }

        showNotification(context, phone, message, recurrence)
    }

    private fun rescheduleNext(context: Context, phone: String, message: String, recurrence: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val next = Calendar.getInstance().apply {
            when (recurrence) {
                1 -> add(Calendar.DAY_OF_YEAR, 1)
                2 -> add(Calendar.WEEK_OF_YEAR, 1)
            }
        }
        val nextIntent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(MainActivity.EXTRA_PHONE, phone)
            putExtra(MainActivity.EXTRA_MSG, message)
            putExtra(MainActivity.EXTRA_RECURRENCE, recurrence)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, MainActivity.REQUEST_CODE, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            // Exact alarm permission revoked; silently skip re-arming.
        }
    }

    private fun showNotification(context: Context, phone: String, message: String, recurrence: Int) {
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
            putExtra(MainActivity.EXTRA_PHONE, phone)
            putExtra(MainActivity.EXTRA_MSG, message)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, MainActivity.REQUEST_CODE, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, SnoozeReceiver::class.java).apply {
            putExtra(MainActivity.EXTRA_PHONE, phone)
            putExtra(MainActivity.EXTRA_MSG, message)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context, MainActivity.REQUEST_CODE + 1, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("WhatsApp reminder")
            .setContentText("Tap to review and send your message")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_popup_reminder, "Snooze 10 min", snoozePendingIntent)
            .build()

        notificationManager.notify(MainActivity.REQUEST_CODE, notification)
    }
}
