package com.example.molmoagent.ui.overlay

import com.example.molmoagent.agent.AgentLoop
import com.example.molmoagent.agent.AgentState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Static holder for the AgentLoop instance so it can be accessed from the overlay service.
 * Set from the Hilt-injected MainActivity/ViewModel.
 */
object AgentLoopHolder {
    var agentLoop: AgentLoop? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentJob: Job? = null

    fun startTask(task: String) {
        val loop = agentLoop ?: return
        // Cancel any still-running coroutine before starting a new one, so we
        // never have two executeTask coroutines alive at the same time.
        currentJob?.cancel()
        currentJob = scope.launch {
            loop.reset()

            // Observe state to hide the panel while running (only glow border visible).
            // The glow overlay is always non-touchable so injected gestures are never
            // intercepted by it — making it touchable caused a race condition where the
            // dispatched gesture hit the glow before FLAG_NOT_TOUCHABLE could propagate.
            val stateObserver = launch {
                loop.state.collect { state ->
                    val service = OverlayService.instance
                    val running = state == AgentState.OBSERVING ||
                            state == AgentState.REASONING ||
                            state == AgentState.ACTING ||
                            state == AgentState.PAUSED
                    service?.setPanelVisible(!running)
                }
            }

            try {
                loop.executeTask(task)
            } finally {
                stateObserver.cancel()
                OverlayService.instance?.setPanelVisible(true)
            }
        }
    }

    fun cancelTask() {
        agentLoop?.cancel()
        currentJob?.cancel()
        currentJob = null
    }
}
