package com.example.molmoagent.agent

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskHistory @Inject constructor() {

    private val steps = mutableListOf<AgentStep>()
    private var currentTaskGoal: String = ""

    fun startNewTask(goal: String) {
        steps.clear()
        currentTaskGoal = goal
    }

    fun addStep(step: AgentStep) {
        steps.add(step)
    }

    fun getSteps(): List<AgentStep> = steps.toList()

    fun getCurrentGoal(): String = currentTaskGoal

    fun getStepCount(): Int = steps.size

    fun getLastStep(): AgentStep? = steps.lastOrNull()

    fun clear() {
        steps.clear()
        currentTaskGoal = ""
    }
}
