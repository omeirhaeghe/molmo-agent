package com.example.molmoagent.ui.overlay

import com.example.molmoagent.agent.AgentLoop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Static holder for the AgentLoop instance so it can be accessed from the overlay service.
 * Set from the Hilt-injected MainActivity/ViewModel.
 */
object AgentLoopHolder {
    var agentLoop: AgentLoop? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun startTask(task: String) {
        agentLoop?.let { loop ->
            loop.reset()
            scope.launch {
                loop.executeTask(task)
            }
        }
    }
}
