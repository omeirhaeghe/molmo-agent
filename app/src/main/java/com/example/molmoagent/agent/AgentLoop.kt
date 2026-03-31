package com.example.molmoagent.agent

import com.example.molmoagent.inference.InferenceClient
import com.example.molmoagent.inference.ImageProcessor
import com.example.molmoagent.screen.ScreenshotManager
import com.example.molmoagent.ui.overlay.OverlayVisibilityController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentLoop @Inject constructor(
    private val screenshotManager: ScreenshotManager,
    private val inferenceClient: InferenceClient,
    private val actionParser: ActionParser,
    private val actionExecutor: ActionExecutor,
    private val taskHistory: TaskHistory,
    private val imageProcessor: ImageProcessor
) {
    private val _state = MutableStateFlow(AgentState.IDLE)
    val state: StateFlow<AgentState> = _state

    private val _currentStep = MutableStateFlow<AgentStep?>(null)
    val currentStep: StateFlow<AgentStep?> = _currentStep

    private val _completionMessage = MutableStateFlow<String?>(null)
    val completionMessage: StateFlow<String?> = _completionMessage

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    var overlayController: OverlayVisibilityController? = null

    suspend fun executeTask(task: String, maxSteps: Int = 15) {
        _state.value = AgentState.OBSERVING
        _completionMessage.value = null
        _errorMessage.value = null
        taskHistory.startNewTask(task)

        for (stepIndex in 1..maxSteps) {
            // Check for pause
            if (_state.value == AgentState.PAUSED) {
                _state.first { it != AgentState.PAUSED }
            }
            if (_state.value == AgentState.CANCELLED) {
                return
            }

            // 1. OBSERVE - capture screenshot
            _state.value = AgentState.OBSERVING
            delay(800) // Wait for UI to settle

            // Hide overlay during screenshot
            overlayController?.hide()
            delay(150)

            val screenshot = screenshotManager.captureScreen()

            overlayController?.show()

            if (screenshot == null) {
                _errorMessage.value = "Failed to capture screenshot"
                _state.value = AgentState.ERROR
                return
            }

            val screenshotBase64 = imageProcessor.toBase64(screenshot)

            // 2. REASON - send to model
            _state.value = AgentState.REASONING
            val response = try {
                inferenceClient.predictNextAction(
                    screenshot = screenshot,
                    taskGoal = task,
                    previousSteps = taskHistory.getSteps()
                )
            } catch (e: Exception) {
                _errorMessage.value = "Inference error: ${e.message}"
                _state.value = AgentState.ERROR
                return
            }

            // 3. PARSE the action
            val action = actionParser.parse(response.rawAction)
            if (action == null) {
                _errorMessage.value = "Could not parse action: ${response.rawAction}"
                _state.value = AgentState.ERROR
                return
            }

            val step = AgentStep(
                stepNumber = stepIndex,
                screenshotBase64 = screenshotBase64,
                thought = response.thought,
                action = action
            )
            _currentStep.value = step

            // 4. Check for completion
            if (action is AgentAction.SendMessageToUser) {
                taskHistory.addStep(step.copy(result = ActionResult.Success))
                _completionMessage.value = action.message
                _state.value = AgentState.COMPLETED
                return
            }

            // 5. EXECUTE the action
            _state.value = AgentState.ACTING
            val result = actionExecutor.execute(action)
            taskHistory.addStep(step.copy(result = result))

            if (result is ActionResult.Failure) {
                // Log the failure but continue - the model will see the unchanged screen
                // and can try a different approach
            }
        }

        _state.value = AgentState.MAX_STEPS_REACHED
        _errorMessage.value = "Reached maximum of $maxSteps steps without completing the task"
    }

    fun pause() {
        if (_state.value == AgentState.OBSERVING || _state.value == AgentState.REASONING || _state.value == AgentState.ACTING) {
            _state.value = AgentState.PAUSED
        }
    }

    fun resume() {
        if (_state.value == AgentState.PAUSED) {
            _state.value = AgentState.OBSERVING
        }
    }

    fun cancel() {
        _state.value = AgentState.CANCELLED
    }

    fun reset() {
        _state.value = AgentState.IDLE
        _currentStep.value = null
        _completionMessage.value = null
        _errorMessage.value = null
        taskHistory.clear()
    }
}
