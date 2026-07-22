package com.drdevrd.whatsappquicksend

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Locale

data class ReminderEntry(
    val id: Int,
    val name: String,
    val phone: String,
    val message: String,
    val triggerAtMillis: Long,
    val recurrence: Int // 0=none, 1=daily, 2=weekly
)

/**
 * Simple persisted list of scheduled reminders, backed by SharedPreferences.
 * Used by MainActivity (to show the list) and by the receivers (to add the
 * next occurrence, or drop one-time reminders once they've fired).
 */
object ReminderStore {
    private const val PREFS = "wa_quick_send_prefs"
    private const val KEY_ENTRIES = "reminders"
    private const val ENTRY_SEP = ";;"
    private const val FIELD_SEP = "|"

    fun nextId(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = prefs.getInt("next_id", 1)
        prefs.edit().putInt("next_id", next + 1).apply()
        return next
    }

    fun add(context: Context, entry: ReminderEntry) {
        val entries = readAll(context).toMutableList()
        entries.add(entry)
        writeAll(context, entries)
    }

    fun remove(context: Context, id: Int) {
        val entries = readAll(context).filterNot { it.id == id }
        writeAll(context, entries)
    }

    fun readAll(context: Context): List<ReminderEntry> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ENTRIES, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(ENTRY_SEP).mapNotNull { line ->
            val parts = line.split(FIELD_SEP)
            if (parts.size != 6) return@mapNotNull null
            try {
                ReminderEntry(
                    id = parts[0].toInt(),
                    name = parts[1],
                    phone = parts[2],
                    message = parts[3],
                    triggerAtMillis = parts[4].toLong(),
                    recurrence = parts[5].toInt()
                )
            } catch (e: NumberFormatException) {
                null
            }
        }
    }

    private fun writeAll(context: Context, entries: List<ReminderEntry>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = entries.joinToString(ENTRY_SEP) { e ->
            listOf(e.id, e.name, e.phone, e.message.replace(FIELD_SEP, " "), e.triggerAtMillis, e.recurrence)
                .joinToString(FIELD_SEP)
        }
        prefs.edit().putString(KEY_ENTRIES, raw).apply()
    }

    fun formatEntry(e: ReminderEntry): String {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        val timeStr = sdf.format(e.triggerAtMillis)
        val repeatLabel = when (e.recurrence) {
            1 -> " (Daily)"
            2 -> " (Weekly)"
            else -> ""
        }
        val label = e.name.ifBlank { e.phone }
        return "$label — $timeStr$repeatLabel"
    }
}
