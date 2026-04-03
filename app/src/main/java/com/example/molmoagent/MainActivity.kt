package com.example.molmoagent

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.molmoagent.accessibility.AgentAccessibilityService
import com.example.molmoagent.ui.chat.ChatScreen
import com.example.molmoagent.ui.chat.ChatViewModel
import com.example.molmoagent.ui.overlay.AgentLoopHolder
import com.example.molmoagent.ui.overlay.OverlayService
import com.example.molmoagent.ui.settings.SettingsScreen
import com.example.molmoagent.ui.setup.SetupScreen
import com.example.molmoagent.ui.theme.MolmoAgentTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MolmoAgentTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val viewModel: ChatViewModel = hiltViewModel()

                    // Wire up the agent loop to the overlay holder
                    LaunchedEffect(viewModel.agentLoop) {
                        AgentLoopHolder.agentLoop = viewModel.agentLoop
                    }

                    // Determine start destination based on permissions
                    val hasPermissions = remember {
                        AgentAccessibilityService.isRunning() &&
                                Settings.canDrawOverlays(this@MainActivity)
                    }

                    val startDestination = if (hasPermissions) "chat" else "setup"

                    NavHost(navController = navController, startDestination = startDestination) {
                        composable("setup") {
                            SetupScreen(
                                onAllPermissionsGranted = {
                                    navController.navigate("chat") {
                                        popUpTo("setup") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("chat") {
                            val tasks by viewModel.tasks.collectAsState()

                            ChatScreen(
                                tasks = tasks,
                                agentLoop = viewModel.agentLoop,
                                onOpenSettings = {
                                    navController.navigate("settings")
                                },
                                onStartOverlay = {
                                    startOverlayService()
                                }
                            )
                        }

                        composable("settings") {
                            val endpointUrl by viewModel.endpointUrl.collectAsState()
                            val qwenEndpointUrl by viewModel.qwenEndpointUrl.collectAsState()
                            val maxSteps by viewModel.maxSteps.collectAsState()
                            val inferenceMode by viewModel.inferenceMode.collectAsState()
                            val downloadState by viewModel.downloadState.collectAsState()
                            val isLoadingModel by viewModel.isLoadingModel.collectAsState()
                            val localModeError by viewModel.localModeError.collectAsState()

                            SettingsScreen(
                                currentEndpointUrl = endpointUrl,
                                currentQwenEndpointUrl = qwenEndpointUrl,
                                currentMaxSteps = maxSteps,
                                currentInferenceMode = inferenceMode,
                                downloadState = downloadState,
                                isLoadingModel = isLoadingModel,
                                localModeError = localModeError,
                                onSave = { url, qwenUrl, steps, mode ->
                                    viewModel.saveSettings(url, qwenUrl, steps, mode)
                                },
                                onTestConnection = { url ->
                                    viewModel.testConnection(url)
                                },
                                onTestQwenConnection = { url ->
                                    viewModel.testConnection(url, isQwen = true)
                                },
                                onInferenceModeChanged = { mode ->
                                    viewModel.setInferenceMode(mode)
                                },
                                onDownloadModel = {
                                    viewModel.downloadModel()
                                },
                                onCancelDownload = {
                                    viewModel.cancelDownload()
                                },
                                onDeleteModel = {
                                    viewModel.deleteModel()
                                },
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startOverlayService() {
        if (Settings.canDrawOverlays(this)) {
            val intent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_SHOW
            }
            startForegroundService(intent)

            // Wire up overlay visibility controller to agent loop
            AgentLoopHolder.agentLoop?.overlayController = OverlayService.instance
        }
    }
}
