package com.example.molmoagent.data

import com.example.molmoagent.agent.ActionResult
import com.example.molmoagent.agent.AgentStep
import com.example.molmoagent.inference.MolmoPromptBuilder
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val stepDao: StepDao,
    private val promptBuilder: MolmoPromptBuilder
) {
    fun getAllTasks(): Flow<List<TaskEntity>> = taskDao.getAllTasks()

    suspend fun createTask(goal: String): Long {
        return taskDao.insert(TaskEntity(goal = goal, status = "RUNNING"))
    }

    suspend fun completeTask(taskId: Long, status: String, message: String?, totalSteps: Int) {
        taskDao.updateTaskResult(taskId, status, message, totalSteps)
    }

    suspend fun saveStep(taskId: Long, step: AgentStep) {
        val actionRaw = formatAction(step)
        val resultStatus = when (step.result) {
            is ActionResult.Success -> "SUCCESS"
            is ActionResult.Failure -> "FAILURE"
            null -> "UNKNOWN"
        }
        val resultMessage = (step.result as? ActionResult.Failure)?.reason

        stepDao.insert(
            StepEntity(
                taskId = taskId,
                stepNumber = step.stepNumber,
                thought = step.thought,
                actionRaw = actionRaw,
                resultStatus = resultStatus,
                resultMessage = resultMessage,
                screenshotBase64 = step.screenshotBase64
            )
        )
    }

    fun getStepsForTask(taskId: Long): Flow<List<StepEntity>> = stepDao.getStepsForTask(taskId)

    private fun formatAction(step: AgentStep): String {
        return step.action.toString()
    }
}
