package com.mediassist.app

import android.app.Application
import com.mediassist.app.data.database.MediAssistDatabase
import com.mediassist.app.domain.repository.MedicationRepository
import com.mediassist.app.domain.repository.UserProfileRepository

class MediAssistApp : Application() {

    val database by lazy { MediAssistDatabase.getDatabase(this) }
    val userProfileRepository by lazy { UserProfileRepository(database.userProfileDao()) }
    val medicationRepository by lazy { MedicationRepository(database.medicationDao()) }

    override fun onCreate() {
        super.onCreate()
        // Initialize app-level dependencies here
    }
}
