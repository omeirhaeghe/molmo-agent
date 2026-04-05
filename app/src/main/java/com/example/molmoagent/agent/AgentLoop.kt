package com.example.molmoagent.agent

import android.util.Log
import com.example.molmoagent.inference.InferenceClient
import com.example.molmoagent.inference.ImageProcessor
import com.example.molmoagent.screen.ScreenshotManager
import com.example.molmoagent.ui.overlay.OverlayVisibilityController
import kotlinx.coroutines.CancellationException
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
            Log.d("Clawlando", "[AgentLoop] loop top, stepIndex=$stepIndex, state=${_state.value}")
            // Check for pause
            if (_state.value == AgentState.PAUSED) {
                _state.first { it != AgentState.PAUSED }
            }
            if (_state.value == AgentState.CANCELLED) {
                Log.d("Clawlando", "[AgentLoop] step $stepIndex: CANCELLED, returning")
                return
            }

            // 1. OBSERVE - capture screenshot
            _state.value = AgentState.OBSERVING
            delay(800) // Wait for UI to settle

            // Hide overlay during screenshot
            overlayController?.hide()
            delay(150)

            val screenshot = screenshotManager.captureScreen()

            overlayController?.showGlowOnly()

            if (screenshot == null) {
                _errorMessage.value = "Failed to capture screenshot"
                _state.value = AgentState.ERROR
                return
            }

            val screenshotBase64 = imageProcessor.toBase64(screenshot)

            // 2. REASON - send to model
            _state.value = AgentState.REASONING
            Log.d("Clawlando", "[AgentLoop] step $stepIndex: calling inference")
            val response = try {
                inferenceClient.predictNextAction(
                    screenshot = screenshot,
                    taskGoal = task,
                    previousSteps = taskHistory.getSteps()
                )
            } catch (e: CancellationException) {
                Log.d("Clawlando", "[AgentLoop] step $stepIndex: inference cancelled")
                throw e
            } catch (e: Exception) {
                Log.e("Clawlando", "[AgentLoop] step $stepIndex: inference error: ${e.message}")
                _errorMessage.value = "Inference error: ${e.message}"
                _state.value = AgentState.ERROR
                return
            }
            Log.d("Clawlando", "[AgentLoop] step $stepIndex: THOUGHT=${response.thought}")
            Log.d("Clawlando", "[AgentLoop] step $stepIndex: inference done, rawAction=${response.rawAction}")

            // 3. PARSE the action
            val action = actionParser.parse(response.rawAction)
            if (action == null) {
                Log.e("Clawlando", "[AgentLoop] step $stepIndex: parse failed for: ${response.rawAction}")
                _errorMessage.value = "Could not parse action: ${response.rawAction}"
                _state.value = AgentState.ERROR
                return
            }
            Log.d("Clawlando", "[AgentLoop] step $stepIndex: parsed action=${action::class.simpleName}")

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
                // Ask the model to narrate what it did in friendly first-person language
                val summary = try {
                    inferenceClient.summarizeTask(task, taskHistory.getSteps())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("Clawlando", "[AgentLoop] summarizeTask failed: ${e.message}")
                    action.message // fall back to the model's original completion message
                }
                _completionMessage.value = summary
                _state.value = AgentState.COMPLETED
                return
            }

            // 5. EXECUTE the action
            _state.value = AgentState.ACTING
            Log.d("Clawlando", "[AgentLoop] step $stepIndex: executing action")
            val result = actionExecutor.execute(action)
            Log.d("Clawlando", "[AgentLoop] step $stepIndex: action result=$result")
            taskHistory.addStep(step.copy(result = result))

            if (result is ActionResult.Failure) {
                Log.w("Clawlando", "[AgentLoop] step $stepIndex: action failed: ${result.reason}")
                // Log the failure but continue - the model will see the unchanged screen
                // and can try a different approach
            }
        }
        Log.d("Clawlando", "[AgentLoop] reached maxSteps=$maxSteps")

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
