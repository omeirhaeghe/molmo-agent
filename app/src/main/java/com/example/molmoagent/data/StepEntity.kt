package com.example.molmoagent.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "steps",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("taskId")]
)
data class StepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val stepNumber: Int,
    val thought: String,
    val actionRaw: String,
    val resultStatus: String, // SUCCESS or FAILURE
    val resultMessage: String? = null,
    val screenshotBase64: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
