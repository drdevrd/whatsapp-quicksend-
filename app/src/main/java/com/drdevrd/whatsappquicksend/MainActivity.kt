package com.drdevrd.whatsappquicksend

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.drdevrd.whatsappquicksend.databinding.ActivityMainBinding
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val pickContactLauncher =
        registerForActivityResult(ActivityResultContracts.PickContact()) { uri: Uri? ->
            uri?.let { readContact(it) }
        }

    private val requestContactsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchContactPicker()
            else Toast.makeText(this, "Contacts permission needed to pick a number", Toast.LENGTH_SHORT).show()
        }

    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // If opened from a scheduled reminder notification, prefill the fields
        intent.getStringExtra(EXTRA_NAME)?.let { binding.etName.setText(it) }
        intent.getStringExtra(EXTRA_PHONE)?.let { binding.etPhone.setText(it) }
        intent.getStringExtra(EXTRA_MSG)?.let { binding.etMessage.setText(it) }

        ArrayAdapter.createFromResource(
            this, R.array.repeat_options, android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerRepeat.adapter = adapter
        }

        binding.btnPickContact.setOnClickListener { checkContactsPermissionAndPick() }
        binding.btnSend.setOnClickListener { sendToWhatsApp() }
        binding.btnSchedule.setOnClickListener { pickDateTime() }

        refreshReminderList()
    }

    override fun onResume() {
        super.onResume()
        refreshReminderList()
    }

    private fun refreshReminderList() {
        val entries = ReminderStore.readAll(this).sortedBy { it.triggerAtMillis }
        binding.tvReminderList.text = if (entries.isEmpty()) {
            "No reminders scheduled yet."
        } else {
            entries.joinToString("\n") { "• " + ReminderStore.formatEntry(it) }
        }
    }

    private fun checkContactsPermissionAndPick() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) launchContactPicker()
        else requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
    }

    private fun launchContactPicker() {
        pickContactLauncher.launch(null)
    }

    private fun readContact(contactUri: Uri) {
        contentResolver.query(contactUri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val hasPhoneIdx = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                if (idIdx < 0 || hasPhoneIdx < 0) return
                val contactId = cursor.getString(idIdx)
                val displayName = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                displayName?.let { binding.etName.setText(it) }

                val hasPhone = cursor.getInt(hasPhoneIdx)
                if (hasPhone > 0) {
                    contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(contactId),
                        null
                    )?.use { phoneCursor ->
                        if (phoneCursor.moveToFirst()) {
                            val numIdx = phoneCursor.getColumnIndex(
                                ContactsContract.CommonDataKinds.Phone.NUMBER
                            )
                            if (numIdx >= 0) {
                                val rawNumber = phoneCursor.getString(numIdx)
                                binding.etPhone.setText(cleanNumber(rawNumber))
                            }
                        }
                    }
                } else {
                    Toast.makeText(this, "No phone number for that contact", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun cleanNumber(raw: String): String {
        return raw.replace("+", "").replace(" ", "").replace("-", "")
            .replace("(", "").replace(")", "")
    }

    private fun sendToWhatsApp() {
        val phone = binding.etPhone.text.toString().trim()
        val message = binding.etMessage.text.toString()

        if (phone.isEmpty()) {
            Toast.makeText(this, "Enter or pick a phone number", Toast.LENGTH_SHORT).show()
            return
        }

        val encodedMessage = Uri.encode(message)
        val uri = Uri.parse("https://wa.me/$phone?text=$encodedMessage")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "WhatsApp not found or invalid number", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pickDateTime() {
        val now = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            TimePickerDialog(this, { _, hour, minute ->
                val cal = Calendar.getInstance().apply {
                    set(year, month, day, hour, minute, 0)
                }
                scheduleReminder(cal.timeInMillis)
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun scheduleReminder(triggerAtMillis: Long) {
        val name = binding.etName.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val message = binding.etMessage.text.toString()

        if (phone.isEmpty()) {
            Toast.makeText(this, "Enter or pick a phone number first", Toast.LENGTH_SHORT).show()
            return
        }

        val recurrence = binding.spinnerRepeat.selectedItemPosition // 0=none, 1=daily, 2=weekly
        val id = ReminderStore.nextId(this) // unique per reminder, so multiple can coexist

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val reminderIntent = Intent(this, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_NAME, name)
            putExtra(EXTRA_PHONE, phone)
            putExtra(EXTRA_MSG, message)
            putExtra(EXTRA_RECURRENCE, recurrence)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this, id, reminderIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                    )
                } else {
                    startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    return
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
            }

            ReminderStore.add(
                this,
                ReminderEntry(id, name, phone, message, triggerAtMillis, recurrence)
            )
            refreshReminderList()

            val repeatLabel = when (recurrence) {
                1 -> " (repeats daily)"
                2 -> " (repeats weekly)"
                else -> ""
            }
            binding.tvScheduledInfo.text = "Reminder set for the chosen time$repeatLabel."
        } catch (e: SecurityException) {
            Toast.makeText(this, "Could not schedule: permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_ID = "extra_id"
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_PHONE = "extra_phone"
        const val EXTRA_MSG = "extra_msg"
        const val EXTRA_RECURRENCE = "extra_recurrence" // 0=none, 1=daily, 2=weekly
    }
}
