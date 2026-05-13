package com.mediassist.app.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class MedicationTakenReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TAKEN = "com.mediassist.app.MEDICATION_TAKEN"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TAKEN) return

        val medicationId = intent.getIntExtra("medication_id", -1)
        if (medicationId == -1) return

        Log.d("MedicationTaken", "Medicamento $medicationId marcado como tomado")

        // Cancel the notification
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(medicationId)
    }
}
