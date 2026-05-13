package com.mediassist.app.domain.repository

import com.mediassist.app.data.database.MedicationDao
import com.mediassist.app.data.model.Medication
import kotlinx.coroutines.flow.Flow

class MedicationRepository(private val dao: MedicationDao) {

    val allMedications: Flow<List<Medication>> = dao.getAllMedications()

    val activeMedications: Flow<List<Medication>> = dao.getActiveMedications()

    fun getMedicationById(id: Int): Flow<Medication?> = dao.getMedicationById(id)

    suspend fun getMedicationByIdSync(id: Int): Medication? = dao.getMedicationByIdSync(id)

    suspend fun insertMedication(medication: Medication): Long = dao.insertMedication(medication)

    suspend fun updateMedication(medication: Medication) = dao.updateMedication(medication)

    suspend fun deleteMedication(medication: Medication) = dao.deleteMedication(medication)
}
