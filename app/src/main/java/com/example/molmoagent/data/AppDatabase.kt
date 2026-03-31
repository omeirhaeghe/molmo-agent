package com.example.molmoagent.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Database(entities = [TaskEntity::class, StepEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun stepDao(): StepDao
}

@Dao
interface TaskDao {
    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTask(id: Long): TaskEntity?

    @Query("UPDATE tasks SET status = :status, completionMessage = :message, totalSteps = :steps WHERE id = :id")
    suspend fun updateTaskResult(id: Long, status: String, message: String?, steps: Int)
}

@Dao
interface StepDao {
    @Insert
    suspend fun insert(step: StepEntity): Long

    @Query("SELECT * FROM steps WHERE taskId = :taskId ORDER BY stepNumber ASC")
    fun getStepsForTask(taskId: Long): Flow<List<StepEntity>>
}
