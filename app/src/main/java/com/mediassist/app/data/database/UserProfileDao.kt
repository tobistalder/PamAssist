/**
 * UserProfileDao — Data Access Object for the patient's demographic and medical profile.
 *
 * Stores vital stats (age, weight) and free-text medical history (conditions, allergies).
 * The latest profile is continuously streamed to the Gemma AI engine to provide
 * contextualized health advice and detect drug/food contraindications.
 */
package com.mediassist.app.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mediassist.app.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfile): Long

    @Update
    suspend fun updateProfile(profile: UserProfile)

    @Delete
    suspend fun deleteProfile(profile: UserProfile)

    /**
     * Get the most recently created user profile reactively.
     * MediAssist is currently a single-user app, so this always fetches the active patient.
     */
    @Query("SELECT * FROM user_profiles ORDER BY id DESC LIMIT 1")
    fun getLatestProfile(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profiles WHERE id = :id")
    fun getProfileById(id: Int): Flow<UserProfile?>

    @Query("SELECT * FROM user_profiles ORDER BY id DESC")
    fun getAllProfiles(): Flow<List<UserProfile>>
}
