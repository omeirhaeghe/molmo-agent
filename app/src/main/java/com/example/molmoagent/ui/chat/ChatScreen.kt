package com.example.molmoagent.ui.chat

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.molmoagent.agent.AgentLoop
import com.example.molmoagent.agent.AgentState
import com.example.molmoagent.data.TaskEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    tasks: List<TaskEntity>,
    agentLoop: AgentLoop,
    onOpenSettings: () -> Unit,
    onStartOverlay: () -> Unit
) {
    val state by agentLoop.state.collectAsState()
    val currentStep by agentLoop.currentStep.collectAsState()
    val completionMessage by agentLoop.completionMessage.collectAsState()
    val errorMessage by agentLoop.errorMessage.collectAsState()

    var taskInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Molmo Agent") },
                actions = {
                    IconButton(onClick = onStartOverlay) {
                        Icon(Icons.Default.Layers, "Show Overlay")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Current agent status
            if (state != AgentState.IDLE) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isRunning = state == AgentState.OBSERVING ||
                                    state == AgentState.REASONING || state == AgentState.ACTING

                            if (isRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            }

                            Text(
                                text = when (state) {
                                    AgentState.OBSERVING -> "Observing screen..."
                                    AgentState.REASONING -> "Thinking..."
                                    AgentState.ACTING -> "Executing action..."
                                    AgentState.PAUSED -> "Paused"
                                    AgentState.COMPLETED -> "Task completed"
                                    AgentState.ERROR -> "Error"
                                    AgentState.CANCELLED -> "Cancelled"
                                    AgentState.MAX_STEPS_REACHED -> "Max steps reached"
                                    else -> state.name
                                },
                                fontWeight = FontWeight.SemiBold,
                                color = when (state) {
                                    AgentState.COMPLETED -> Color(0xFF4CAF50)
                                    AgentState.ERROR, AgentState.MAX_STEPS_REACHED -> Color(0xFFF44336)
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }

                        currentStep?.let { step ->
                            if (step.thought.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Step ${step.stepNumber}: ${step.thought}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        completionMessage?.let { msg ->
                            Spacer(Modifier.height(8.dp))
                            Text(msg, color = Color(0xFF4CAF50), fontSize = 13.sp)
                        }

                        errorMessage?.let { msg ->
                            Spacer(Modifier.height(8.dp))
                            Text(msg, color = Color(0xFFF44336), fontSize = 13.sp)
                        }
                    }
                }
            }

            // Task history
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (tasks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.SmartToy,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "No tasks yet",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Text(
                                    "Enter a task below to get started",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }

                items(tasks) { task ->
                    TaskCard(task)
                }
            }

            // Input bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = taskInput,
                        onValueChange = { taskInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("What should I do?") },
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true
                    )

                    FilledIconButton(
                        onClick = {
                            if (taskInput.isNotBlank() && state == AgentState.IDLE) {
                                val task = taskInput.trim()
                                taskInput = ""
                                agentLoop.reset()
                                scope.launch {
                                    agentLoop.executeTask(task)
                                }
                            }
                        },
                        enabled = taskInput.isNotBlank() && state == AgentState.IDLE
                    ) {
                        Icon(Icons.Default.Send, "Send")
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(task: TaskEntity) {
    Card(
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when (task.status) {
                    "COMPLETED" -> Icons.Default.CheckCircle
                    "ERROR" -> Icons.Default.Error
                    "CANCELLED" -> Icons.Default.Cancel
                    else -> Icons.Default.HourglassEmpty
                },
                contentDescription = null,
                tint = when (task.status) {
                    "COMPLETED" -> Color(0xFF4CAF50)
                    "ERROR" -> Color(0xFFF44336)
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                }
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task.goal,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${task.totalSteps} steps",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    task.completionMessage?.let { msg ->
                        Text(
                            msg,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
