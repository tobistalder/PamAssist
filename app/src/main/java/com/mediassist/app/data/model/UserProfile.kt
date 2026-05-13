package com.mediassist.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val surname: String,
    val birthDate: String,
    val weight: Float,
    val medicalHistory: String
) {
    fun calculateAge(): Int {
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val birth = LocalDate.parse(birthDate, formatter)
        return Period.between(birth, LocalDate.now()).years
    }
}
