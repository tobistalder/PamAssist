package com.mediassist.app.domain.repository

import com.mediassist.app.data.database.UserProfileDao
import com.mediassist.app.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

class UserProfileRepository(private val dao: UserProfileDao) {

    val latestProfile: Flow<UserProfile?> = dao.getLatestProfile()

    val allProfiles: Flow<List<UserProfile>> = dao.getAllProfiles()

    fun getProfileById(id: Int): Flow<UserProfile?> = dao.getProfileById(id)

    suspend fun insertProfile(profile: UserProfile): Long = dao.insertProfile(profile)

    suspend fun updateProfile(profile: UserProfile) = dao.updateProfile(profile)

    suspend fun deleteProfile(profile: UserProfile) = dao.deleteProfile(profile)
}
