/**
 * AlarmScheduler — Manages exact offline notifications for medication reminders.
 *
 * This class abstracts the Android AlarmManager API to schedule precise alarms
 * for each medication. It's critical for the app's "offline-first" functionality,
 * ensuring seniors get their reminders on time without relying on FCM/Push notifications
 * or an active internet connection.
 *
 * Key behaviors:
 * 1. Uses `setExactAndAllowWhileIdle` to bypass Android Doze mode restrictions.
 * 2. Fallbacks gracefully to inexact alarms if exact alarm permissions (SCHEDULE_EXACT_ALARM)
 *    are revoked by the OS (Android 12+).
 * 3. Before rescheduling the entire list of medications, it completely purges old alarms
 *    to prevent duplicate triggers.
 */
package com.mediassist.app.domain

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.mediassist.app.data.model.Medication
import com.mediassist.app.receiver.MedicationAlarmReceiver
import java.util.Calendar

class AlarmScheduler(private val context: Context) {

    /**
     * Re-schedule all alarms for a list of medications.
     * Always cancels existing alarms first to avoid duplicates.
     */
    fun scheduleAll(medications: List<Medication>) {
        cancelAll(medications)
        medications.filter { it.active }.forEach { med ->
            // ScheduleTimes is a comma-separated string (e.g., "08:00,20:00")
            med.scheduleTimes.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.contains(":") }
                .forEach { time ->
                    schedule(med, time)
                }
        }
    }

    /**
     * Schedule a single daily exact alarm for a specific medication.
     * If the time for today has already passed, it automatically schedules for tomorrow.
     */
    fun schedule(medication: Medication, time: String) {
        try {
            val parts = time.split(":")
            if (parts.size < 2) return
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                // If the time already passed today, shift the alarm to tomorrow
                if (before(Calendar.getInstance())) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            // Create the intent that will trigger the MedicationAlarmReceiver
            val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
                action = MedicationAlarmReceiver.ACTION_ALARM
                putExtra("medication_id", medication.id)
                putExtra("medication_name", medication.name)
                putExtra("medication_dose", medication.dose)
                putExtra("medication_time", time)
                putExtra("medication_schedule_times", medication.scheduleTimes)
            }

            // Unique request code per medication and time combination
            val requestCode = "${medication.id}_${time.replace(":", "")}".hashCode()

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = context.getSystemService(AlarmManager::class.java)

            // CRITICAL: Schedule the alarm using exact APIs so Doze mode doesn't delay it.
            // Senior citizens need medications precisely on time.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    // Fallback: use inexact alarm if the user manually revoked the permission
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                    Log.w("AlarmScheduler", "Exact alarm permission not granted, using inexact alarm")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }

            Log.d("AlarmScheduler", "Alarma programada: ${medication.name} a las $time -> ${calendar.time} (requestCode=$requestCode)")
        } catch (e: Exception) {
            Log.e("AlarmScheduler", "Error programando alarma para ${medication.name} a las $time: ${e.message}")
        }
    }

    /**
     * Iterates over all medications and cancels their pending AlarmManager intents.
     */
    fun cancelAll(medications: List<Medication>) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        medications.forEach { med ->
            med.scheduleTimes.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.contains(":") }
                .forEach { time ->
                    val requestCode = "${med.id}_${time.replace(":", "")}".hashCode()
                    val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
                        action = MedicationAlarmReceiver.ACTION_ALARM
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        context, requestCode, intent,
                        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                    )
                    pendingIntent?.let {
                        alarmManager.cancel(it)
                        it.cancel() // Also cancel the PendingIntent itself at the OS level
                        Log.d("AlarmScheduler", "Alarma cancelada: ${med.name} a las $time")
                    }
                }
        }
    }
}
