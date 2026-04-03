package com.example.molmoagent.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.molmoagent.inference.InferenceClientManager.InferenceMode
import com.example.molmoagent.inference.local.ModelDownloadManager.DownloadState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentEndpointUrl: String,
    currentMaxSteps: Int,
    currentInferenceMode: InferenceMode,
    downloadState: DownloadState,
    isLoadingModel: Boolean,
    localModeError: String?,
    onSave: (endpointUrl: String, maxSteps: Int, mode: InferenceMode) -> Unit,
    onTestConnection: suspend (url: String) -> Boolean,
    onInferenceModeChanged: (InferenceMode) -> Unit,
    onDownloadModel: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteModel: () -> Unit,
    onBack: () -> Unit
) {
    var endpointUrl by remember { mutableStateOf(currentEndpointUrl) }
    var maxSteps by remember { mutableStateOf(currentMaxSteps.toString()) }
    var selectedMode by remember { mutableStateOf(currentInferenceMode) }
    var testResult by remember { mutableStateOf<Boolean?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Inference Backend Selection
            Text(
                "Inference Backend",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterChip(
                    selected = selectedMode == InferenceMode.CLOUD,
                    onClick = {
                        selectedMode = InferenceMode.CLOUD
                        onInferenceModeChanged(InferenceMode.CLOUD)
                    },
                    label = { Text("Cloud") },
                    leadingIcon = {
                        Icon(Icons.Default.Cloud, null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedMode == InferenceMode.LOCAL,
                    onClick = {
                        selectedMode = InferenceMode.LOCAL
                        if (downloadState is DownloadState.Downloaded) {
                            onInferenceModeChanged(InferenceMode.LOCAL)
                        }
                    },
                    enabled = downloadState !is DownloadState.Downloading,
                    label = { Text("Local") },
                    leadingIcon = {
                        Icon(Icons.Default.PhoneAndroid, null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Cloud Settings (show when cloud is selected)
            if (selectedMode == InferenceMode.CLOUD) {
                Text(
                    "Cloud Inference (MolmoWeb-8B)",
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                )

                OutlinedTextField(
                    value = endpointUrl,
                    onValueChange = {
                        endpointUrl = it
                        testResult = null
                    },
                    label = { Text("HuggingFace Endpoint URL") },
                    placeholder = { Text("https://your-endpoint.endpoints.huggingface.cloud") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Cloud, null) }
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            isTesting = true
                            testResult = null
                            scope.launch {
                                testResult = onTestConnection(endpointUrl)
                                isTesting = false
                            }
                        },
                        enabled = endpointUrl.isNotBlank() && !isTesting,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Test Connection")
                    }

                    testResult?.let { success ->
                        if (success) {
                            Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50))
                            Text("Connected", color = Color(0xFF4CAF50), fontSize = 13.sp)
                        } else {
                            Text("Failed to connect", color = Color(0xFFF44336), fontSize = 13.sp)
                        }
                    }
                }
            }

            // Local Settings (show when local is selected)
            if (selectedMode == InferenceMode.LOCAL) {
                Text(
                    "Local Inference (SmolVLM2-2.2B)",
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                )

                Text(
                    "Runs entirely on your device. No cloud server needed. Requires ~3GB RAM.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                // Show error if model loading failed
                localModeError?.let { error ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF44336).copy(alpha = 0.1f)
                        )
                    ) {
                        Text(
                            text = error,
                            color = Color(0xFFF44336),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                LocalModelSection(
                    downloadState = downloadState,
                    isLoadingModel = isLoadingModel,
                    onDownload = onDownloadModel,
                    onCancel = onCancelDownload,
                    onDelete = onDeleteModel
                )
            }

            Spacer(modifier = Modifier.height(1.dp))

            // Agent Settings
            Text(
                "Agent Settings",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            OutlinedTextField(
                value = maxSteps,
                onValueChange = { maxSteps = it.filter { c -> c.isDigit() } },
                label = { Text("Max steps per task") },
                modifier = Modifier.width(120.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Text(
                "The agent will stop after this many steps if the task isn't complete.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Warn if LOCAL mode is selected but model isn't ready
            if (selectedMode == InferenceMode.LOCAL && downloadState !is DownloadState.Downloaded) {
                Text(
                    "Download the model first to use local inference.",
                    fontSize = 12.sp,
                    color = Color(0xFFFFA726)
                )
            }

            Button(
                onClick = {
                    val modeToSave = if (selectedMode == InferenceMode.LOCAL && downloadState !is DownloadState.Downloaded) {
                        InferenceMode.CLOUD
                    } else {
                        selectedMode
                    }
                    onSave(
                        endpointUrl,
                        maxSteps.toIntOrNull()?.coerceIn(1, 50) ?: 15,
                        modeToSave
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Settings")
            }
        }
    }
}

@Composable
private fun LocalModelSection(
    downloadState: DownloadState,
    isLoadingModel: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Model Status", fontWeight = FontWeight.Medium, fontSize = 14.sp)

                when (downloadState) {
                    is DownloadState.NotDownloaded -> {
                        Text("Not downloaded", fontSize = 13.sp, color = Color(0xFFFFA726))
                    }
                    is DownloadState.Downloaded -> {
                        Text("Ready", fontSize = 13.sp, color = Color(0xFF4CAF50))
                    }
                    is DownloadState.Downloading -> {
                        Text("Downloading...", fontSize = 13.sp, color = Color(0xFF4FC3F7))
                    }
                    is DownloadState.Error -> {
                        Text("Error", fontSize = 13.sp, color = Color(0xFFF44336))
                    }
                }
            }

            when (downloadState) {
                is DownloadState.NotDownloaded -> {
                    Text(
                        "SmolVLM2-2.2B-Instruct (Q4_K_M)\nDownload size: ~1.7 GB",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Download Model")
                    }
                }

                is DownloadState.Downloading -> {
                    @Suppress("DEPRECATION")
                    LinearProgressIndicator(
                        progress = downloadState.progressPercent / 100f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        downloadState.description.ifEmpty { "${downloadState.progressPercent}%" },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancel")
                    }
                }

                is DownloadState.Downloaded -> {
                    if (isLoadingModel) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Text("Loading model into memory...", fontSize = 13.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFF44336)
                        )
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Delete Model")
                    }
                }

                is DownloadState.Error -> {
                    Text(
                        "Error: ${downloadState.message}",
                        fontSize = 12.sp,
                        color = Color(0xFFF44336)
                    )
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Retry Download")
                    }
                }
            }
        }
    }
}
