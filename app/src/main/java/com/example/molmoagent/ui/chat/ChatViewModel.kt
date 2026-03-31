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
import com.example.molmoagent.inference.HuggingFaceClient
import com.example.molmoagent.inference.InferenceClient
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
    private val inferenceClient: InferenceClient
) : ViewModel() {

    companion object {
        val ENDPOINT_URL_KEY = stringPreferencesKey("endpoint_url")
        val MAX_STEPS_KEY = intPreferencesKey("max_steps")
    }

    val tasks: StateFlow<List<TaskEntity>> = taskRepository.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val endpointUrl: StateFlow<String> = context.dataStore.data
        .map { it[ENDPOINT_URL_KEY] ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val maxSteps: StateFlow<Int> = context.dataStore.data
        .map { it[MAX_STEPS_KEY] ?: 15 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 15)

    init {
        // Apply saved settings to the inference client
        viewModelScope.launch {
            context.dataStore.data.collect { prefs ->
                val url = prefs[ENDPOINT_URL_KEY] ?: ""
                if (url.isNotEmpty() && inferenceClient is HuggingFaceClient) {
                    inferenceClient.endpointUrl = url
                }
            }
        }
    }

    fun saveSettings(endpointUrl: String, maxSteps: Int) {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[ENDPOINT_URL_KEY] = endpointUrl
                prefs[MAX_STEPS_KEY] = maxSteps
            }
            if (inferenceClient is HuggingFaceClient) {
                inferenceClient.endpointUrl = endpointUrl
            }
        }
    }

    suspend fun testConnection(url: String): Boolean {
        if (inferenceClient is HuggingFaceClient) {
            val original = inferenceClient.endpointUrl
            inferenceClient.endpointUrl = url
            val result = inferenceClient.testConnection()
            inferenceClient.endpointUrl = original
            return result
        }
        return false
    }
}
