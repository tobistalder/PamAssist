package com.mediassist.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val dose: String,
    val frequencyHours: Int,
    val scheduleTimes: String,
    val active: Boolean = true
)
