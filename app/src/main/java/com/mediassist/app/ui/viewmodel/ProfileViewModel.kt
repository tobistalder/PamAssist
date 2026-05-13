/**
 * ProfileViewModel — Manages the state of the patient's medical profile.
 *
 * Handles the creation, editing, and validation of the user's demographic data
 * (age, weight) and medical history. This data is critical as it acts as the 
 * contextual baseline injected into every Gemma 4 inference call via CactusManager,
 * enabling personalized AI assistance.
 */
package com.mediassist.app.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mediassist.app.data.model.UserProfile
import com.mediassist.app.domain.repository.UserProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.firstOrNull


class ProfileViewModel(
    private val repository: UserProfileRepository
) : ViewModel() {

    val latestProfile = repository.latestProfile

    // Basic profile fields
    var name by mutableStateOf("")
    var surname by mutableStateOf("")
    var birthDate by mutableStateOf("")
    var weight by mutableStateOf("")

    // Medical history fields
    var medicalHistoryInput by mutableStateOf("")

    init {
        // Pre-fill fields if a profile already exists
        viewModelScope.launch {
            val existing = repository.latestProfile.firstOrNull()
            if (existing != null) {
                name = existing.name
                surname = existing.surname
                birthDate = existing.birthDate
                weight = existing.weight.toString()
                medicalHistoryInput = existing.medicalHistory
            }
        }
    }

    // Save state
    private val _saveComplete = MutableStateFlow(false)
    val saveComplete: StateFlow<Boolean> = _saveComplete.asStateFlow()

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    /** Ensures critical fields exist before allowing the profile to be saved */
    fun validateBasicProfile(): Boolean {
        return name.isNotBlank() &&
                surname.isNotBlank() &&
                birthDate.isNotBlank() &&
                birthDate.matches(Regex("^\\d{2}/\\d{2}/\\d{4}$")) &&
                weight.isNotBlank() &&
                weight.toFloatOrNull() != null
    }

    fun saveProfile() {
        viewModelScope.launch {
            try {
                val profile = UserProfile(
                    name = name.trim(),
                    surname = surname.trim(),
                    birthDate = birthDate,
                    weight = weight.toFloatOrNull() ?: 0f,
                    medicalHistory = medicalHistoryInput
                )
                repository.insertProfile(profile)
                _saveMessage.value = "Profile saved successfully!"
                _saveComplete.value = true
            } catch (e: Exception) {
                _saveMessage.value = "Error saving profile: ${e.message}"
            }
        }
    }

    fun clearSaveMessage() {
        _saveMessage.value = null
    }

    fun resetSaveComplete() {
        _saveComplete.value = false
    }

    fun resetAll() {
        name = ""
        surname = ""
        birthDate = ""
        weight = ""
        medicalHistoryInput = ""
        _saveComplete.value = false
        _saveMessage.value = null
    }
}

class ProfileViewModelFactory(
    private val repository: UserProfileRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
