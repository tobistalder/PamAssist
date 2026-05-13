/**
 * MedicationAlarmReceiver — Broadcast receiver triggered by AlarmManager.
 *
 * This component fires when it is time for the patient to take their medication.
 * It is responsible for:
 * 1. Validating against the database that the medication was not deleted or deactivated
 *    ("Phantom Alarm" prevention).
 * 2. Creating a high-priority system notification to alert the user.
 * 3. Rescheduling the alarm for the exact same time the following day.
 */
package com.mediassist.app.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mediassist.app.R
import com.mediassist.app.data.model.Medication
import com.mediassist.app.domain.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MedicationAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "medication_reminders"
        const val ACTION_ALARM = "com.mediassist.app.MEDICATION_ALARM"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM) return

        val medicationId = intent.getIntExtra("medication_id", -1)
        val medicationName = intent.getStringExtra("medication_name") ?: return
        val medicationDose = intent.getStringExtra("medication_dose") ?: ""
        val medicationTime = intent.getStringExtra("medication_time") ?: ""
        val medicationScheduleTimes = intent.getStringExtra("medication_schedule_times") ?: ""

        Log.d("MedicationAlarm", "Alarma recibida: $medicationName a las $medicationTime")

        // CRITICAL BUGFIX: Prevent Phantom Alarms.
        // Before showing the notification, we query the DB to ensure the medication
        // wasn't deleted while the app was closed. Without this, the OS might fire
        // an outdated PendingIntent.
        val pendingResult = goAsync() // Hold the WakeLock so coroutine can finish
        val db = com.mediassist.app.data.database.MediAssistDatabase.getDatabase(context)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Synchronously query the DB for the medication state
                val medication = db.medicationDao().getMedicationByIdSync(medicationId)
                
                // If it was deleted or deactivated, abort quietly
                if (medication == null || !medication.active) {
                    Log.d("MedicationAlarm", "Medicamento eliminado o inactivo. Ignorando alarma y no reprogramando.")
                    return@launch
                }

                // Create notification channel (required for Android 8.0+)
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "Recordatorios de medicamentos",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Notificaciones para recordar tomar medicamentos"
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                // PendingIntent for the "Tomé la pastilla" (I took the pill) notification action button
                val takenIntent = Intent(context, MedicationTakenReceiver::class.java).apply {
                    action = MedicationTakenReceiver.ACTION_TAKEN
                    putExtra("medication_id", medicationId)
                }
                val takenPendingIntent = PendingIntent.getBroadcast(
                    context,
                    medicationId,
                    takenIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // Build and show the high-priority notification
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("\uD83D\uDC8A Hora de tu medicamento")
                    .setContentText("${medication.name} — ${medication.dose}")
                    .setPriority(NotificationCompat.PRIORITY_HIGH) // Pops up heads-up display
                    .setAutoCancel(true)
                    .addAction(0, "Tomé el medicamento", takenPendingIntent)
                    .build()

                notificationManager.notify(medicationId, notification)

                // Reschedule this specific alarm for tomorrow so it repeats infinitely
                if (medicationTime.isNotEmpty()) {
                    AlarmScheduler(context).schedule(medication, medicationTime)
                }
            } catch (e: Exception) {
                Log.e("MedicationAlarm", "Error validando la alarma en DB: ${e.message}")
            } finally {
                pendingResult.finish() // Release the BroadcastReceiver WakeLock
            }
        }
    }
}
