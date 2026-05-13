/**
 * MedicationDao — Data Access Object for the Medications table.
 *
 * Provides reactive Kotlin Flow queries for real-time UI updates (e.g., MedicationsScreen)
 * and synchronous suspends/functions for background workers like AlarmReceiver.
 */
package com.mediassist.app.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mediassist.app.data.model.Medication
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedication(medication: Medication): Long

    @Update
    suspend fun updateMedication(medication: Medication)

    @Delete
    suspend fun deleteMedication(medication: Medication)

    /** Observe all medications reactively. Used to populate the settings/management list. */
    @Query("SELECT * FROM medications ORDER BY name ASC")
    fun getAllMedications(): Flow<List<Medication>>

    /** Observe only active medications. Used to inject current context into Gemma 4 system prompt. */
    @Query("SELECT * FROM medications WHERE active = 1 ORDER BY name ASC")
    fun getActiveMedications(): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE id = :id")
    fun getMedicationById(id: Int): Flow<Medication?>

    /** 
     * Synchronous fetch for background receivers.
     * Used heavily by [MedicationAlarmReceiver] to prevent phantom alarms for deleted meds.
     */
    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getMedicationByIdSync(id: Int): Medication?
}
