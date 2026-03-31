package com.example.molmoagent.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentEndpointUrl: String,
    currentMaxSteps: Int,
    onSave: (endpointUrl: String, maxSteps: Int) -> Unit,
    onTestConnection: suspend (url: String) -> Boolean,
    onBack: () -> Unit
) {
    var endpointUrl by remember { mutableStateOf(currentEndpointUrl) }
    var maxSteps by remember { mutableStateOf(currentMaxSteps.toString()) }
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Inference Endpoint
            Text(
                "Inference Server",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
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

            Button(
                onClick = {
                    onSave(
                        endpointUrl,
                        maxSteps.toIntOrNull()?.coerceIn(1, 50) ?: 15
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
