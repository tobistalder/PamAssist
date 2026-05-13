/**
 * MedicationsViewModel — Manages the state and logic for the Medications tracking screen.
 *
 * This ViewModel bridges the UI with the local Room Database and the AlarmScheduler.
 * It provides:
 * 1. A reactive stream of all medications via StateFlow.
 * 2. Logic for adding, editing, and deleting medications.
 * 3. Automatic alarm rescheduling whenever the active medication list changes,
 *    ensuring the offline notification system is always perfectly synchronized.
 */
package com.mediassist.app.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mediassist.app.data.model.Medication
import com.mediassist.app.domain.AlarmScheduler
import com.mediassist.app.domain.repository.MedicationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MedicationsViewModel(
    private val repository: MedicationRepository,
    private val context: Context
) : ViewModel() {

    private val alarmScheduler = AlarmScheduler(context)

    /** Reactive stream of all medications from Room, kept alive while subscribed */
    val medications: StateFlow<List<Medication>> = repository.allMedications
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Dialog state
    var showAddDialog by mutableStateOf(false)
    var editingMedication by mutableStateOf<Medication?>(null)
    var medName by mutableStateOf("")
    var medDose by mutableStateOf("")
    var medFrequencyHours by mutableStateOf("")
    var medScheduleTimes by mutableStateOf("")

    // Feedback message for snackbars
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun openAddDialog(medication: Medication? = null) {
        editingMedication = medication
        if (medication != null) {
            medName = medication.name
            medDose = medication.dose
            medFrequencyHours = medication.frequencyHours.toString()
            medScheduleTimes = medication.scheduleTimes
        } else {
            medName = ""
            medDose = ""
            medFrequencyHours = ""
            medScheduleTimes = ""
        }
        showAddDialog = true
    }

    fun closeAddDialog() {
        showAddDialog = false
    }

    fun saveMedication() {
        if (medName.isBlank() || medDose.isBlank()) {
            _message.value = "Please fill in medication name and dose."
            return
        }

        viewModelScope.launch {
            try {
                if (editingMedication != null) {
                    val medication = editingMedication!!.copy(
                        name = medName.trim(),
                        dose = medDose.trim(),
                        frequencyHours = medFrequencyHours.toIntOrNull() ?: 0,
                        scheduleTimes = medScheduleTimes.trim()
                    )
                    // Cancel old alarms before updating
                    alarmScheduler.cancelAll(listOf(editingMedication!!))
                    repository.updateMedication(medication)
                    _message.value = "Medication \"${medication.name}\" updated successfully!"
                } else {
                    val medication = Medication(
                        name = medName.trim(),
                        dose = medDose.trim(),
                        frequencyHours = medFrequencyHours.toIntOrNull() ?: 0,
                        scheduleTimes = medScheduleTimes.trim(),
                        active = true
                    )
                    repository.insertMedication(medication)
                    _message.value = "Medication \"${medication.name}\" added successfully!"
                }
                showAddDialog = false
                editingMedication = null
                rescheduleAlarms()
            } catch (e: Exception) {
                _message.value = "Error saving medication: ${e.message}"
            }
        }
    }

    fun toggleMedication(medication: Medication) {
        viewModelScope.launch {
            try {
                alarmScheduler.cancelAll(listOf(medication))
                val updated = medication.copy(active = !medication.active)
                repository.updateMedication(updated)
                val status = if (updated.active) "activated" else "deactivated"
                _message.value = "${medication.name} $status."
                rescheduleAlarms()
            } catch (e: Exception) {
                _message.value = "Error updating medication: ${e.message}"
            }
        }
    }

    fun deleteMedication(medication: Medication) {
        viewModelScope.launch {
            try {
                // Important: clear alarms before deleting from DB
                alarmScheduler.cancelAll(listOf(medication))
                repository.deleteMedication(medication)
                _message.value = "${medication.name} removed."
                rescheduleAlarms()
            } catch (e: Exception) {
                _message.value = "Error removing medication: ${e.message}"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    /** Ensure AlarmManager is fully synced with the current DB state */
    private fun rescheduleAlarms() {
        viewModelScope.launch {
            val allMeds = repository.allMedications.firstOrNull() ?: emptyList()
            alarmScheduler.scheduleAll(allMeds)
        }
    }
}

class MedicationsViewModelFactory(
    private val repository: MedicationRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MedicationsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MedicationsViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
