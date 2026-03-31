package com.example.molmoagent.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goal: String,
    val status: String, // COMPLETED, ERROR, CANCELLED, MAX_STEPS
    val completionMessage: String? = null,
    val totalSteps: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
