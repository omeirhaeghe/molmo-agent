package com.example.molmoagent.ui.chat

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.molmoagent.agent.AgentLoop
import com.example.molmoagent.data.TaskEntity
import com.example.molmoagent.data.TaskRepository
import com.example.molmoagent.inference.InferenceClientManager
import com.example.molmoagent.inference.local.LlamaCppBridge
import com.example.molmoagent.inference.local.ModelDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val agentLoop: AgentLoop,
    private val taskRepository: TaskRepository,
    private val inferenceClientManager: InferenceClientManager,
    val downloadManager: ModelDownloadManager,
    private val llamaBridge: LlamaCppBridge
) : ViewModel() {

    companion object {
        val ENDPOINT_URL_KEY = stringPreferencesKey("endpoint_url")
        val QWEN_ENDPOINT_URL_KEY = stringPreferencesKey("qwen_endpoint_url") // legacy, read for migration
        val MAX_STEPS_KEY = intPreferencesKey("max_steps")
        val INFERENCE_MODE_KEY = stringPreferencesKey("inference_mode")
        const val DEFAULT_ENDPOINT_URL = "https://dmuxtqvqoal6yvu1.us-east-1.aws.endpoints.huggingface.cloud"
    }

    val tasks: StateFlow<List<TaskEntity>> = taskRepository.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val endpointUrl: StateFlow<String> = context.dataStore.data
        .map { it[ENDPOINT_URL_KEY] ?: DEFAULT_ENDPOINT_URL }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_ENDPOINT_URL)

    val maxSteps: StateFlow<Int> = context.dataStore.data
        .map { it[MAX_STEPS_KEY] ?: 15 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 15)

    val inferenceMode: StateFlow<InferenceClientManager.InferenceMode> = inferenceClientManager.mode

    val downloadState: StateFlow<ModelDownloadManager.DownloadState> = downloadManager.downloadState

    private val _isLoadingModel = MutableStateFlow(false)
    val isLoadingModel: StateFlow<Boolean> = _isLoadingModel

    init {
        // Check download status on init (before restoring mode)
        downloadManager.checkDownloadStatus()

        // Apply saved settings — use first() to avoid re-triggering on every dataStore write
        viewModelScope.launch {
            val prefs = context.dataStore.data.first()
            // Migration: if legacy QWEN mode was saved, use its endpoint as the cloud endpoint
            val savedMode = prefs[INFERENCE_MODE_KEY] ?: "QWEN"
            val url = when {
                prefs[ENDPOINT_URL_KEY]?.isNotEmpty() == true -> prefs[ENDPOINT_URL_KEY]!!
                savedMode == "QWEN" -> prefs[QWEN_ENDPOINT_URL_KEY] ?: DEFAULT_ENDPOINT_URL
                else -> DEFAULT_ENDPOINT_URL
            }
            inferenceClientManager.endpointUrl = url

            when (savedMode) {
                "LOCAL" -> if (downloadManager.isDownloaded) {
                    _isLoadingModel.value = true
                    val result = inferenceClientManager.switchToLocal()
                    _isLoadingModel.value = false
                    if (result.isFailure) {
                        _localModeError.value = result.exceptionOrNull()?.message
                            ?: "Failed to load model on startup"
                    }
                }
                else -> inferenceClientManager.switchToCloud(unloadLocal = false)
            }
        }
    }

    fun saveSettings(endpointUrl: String, maxSteps: Int, mode: InferenceClientManager.InferenceMode) {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[ENDPOINT_URL_KEY] = endpointUrl
                prefs[MAX_STEPS_KEY] = maxSteps
                prefs[INFERENCE_MODE_KEY] = mode.name
            }
            inferenceClientManager.endpointUrl = endpointUrl
        }
    }

    private val _localModeError = MutableStateFlow<String?>(null)
    val localModeError: StateFlow<String?> = _localModeError

    fun clearLocalModeError() {
        _localModeError.value = null
    }

    fun setInferenceMode(mode: InferenceClientManager.InferenceMode) {
        viewModelScope.launch {
            when (mode) {
                InferenceClientManager.InferenceMode.LOCAL -> {
                    if (!LlamaCppBridge.isAvailable) {
                        _localModeError.value = "Local inference not available: native library failed to load"
                        inferenceClientManager.switchToCloud(unloadLocal = false)
                        return@launch
                    }
                    _localModeError.value = null
                    _isLoadingModel.value = true
                    val result = inferenceClientManager.switchToLocal()
                    _isLoadingModel.value = false
                    if (result.isFailure) {
                        _localModeError.value = result.exceptionOrNull()?.message
                            ?: "Failed to load model"
                        inferenceClientManager.switchToCloud(unloadLocal = false)
                        return@launch
                    }
                }
                InferenceClientManager.InferenceMode.CLOUD -> {
                    inferenceClientManager.switchToCloud()
                }
            }

            context.dataStore.edit { prefs ->
                prefs[INFERENCE_MODE_KEY] = mode.name
            }
        }
    }

    fun downloadModel() {
        viewModelScope.launch {
            downloadManager.downloadModels()
        }
    }

    fun cancelDownload() {
        downloadManager.cancelDownload()
    }

    fun deleteModel() {
        viewModelScope.launch {
            if (inferenceMode.value == InferenceClientManager.InferenceMode.LOCAL) {
                inferenceClientManager.switchToCloud()
            }
            downloadManager.deleteModels()
        }
    }

    suspend fun testConnection(url: String): Boolean {
        val original = inferenceClientManager.endpointUrl
        inferenceClientManager.endpointUrl = url
        val result = try {
            inferenceClientManager.testConnection()
        } catch (_: Exception) {
            false
        }
        inferenceClientManager.endpointUrl = original
        return result
    }
}
