/**
 * MediAssistDatabase — Room Database instance for local offline persistence.
 *
 * This singleton database stores user demographics, medical history, and 
 * medication alarm schedules. It enables the application to function 100% offline
 * in conjunction with the local Gemma AI model, ensuring that patient data
 * never leaves the device.
 */
package com.mediassist.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mediassist.app.data.model.Medication
import com.mediassist.app.data.model.UserProfile

@Database(
    entities = [UserProfile::class, Medication::class],
    version = 2,
    exportSchema = false
)
abstract class MediAssistDatabase : RoomDatabase() {

    abstract fun userProfileDao(): UserProfileDao
    abstract fun medicationDao(): MedicationDao

    companion object {
        @Volatile
        private var INSTANCE: MediAssistDatabase? = null

        /**
         * Get the singleton instance of the Room database.
         * Falls back to destructive migration (wipes data) if the schema version
         * is upgraded without a migration path provided.
         */
        fun getDatabase(context: Context): MediAssistDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MediAssistDatabase::class.java,
                    "mediassist_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
