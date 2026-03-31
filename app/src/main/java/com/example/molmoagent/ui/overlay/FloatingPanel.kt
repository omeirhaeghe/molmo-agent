package com.example.molmoagent.ui.overlay

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.molmoagent.agent.AgentState

@Composable
fun FloatingPanel(
    onSubmitTask: (String) -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onOpenApp: () -> Unit
) {
    val agentLoop = AgentLoopHolder.agentLoop
    val state by agentLoop?.state?.collectAsState() ?: remember { mutableStateOf(AgentState.IDLE) }
    val currentStep by agentLoop?.currentStep?.collectAsState() ?: remember { mutableStateOf(null) }
    val completionMessage by agentLoop?.completionMessage?.collectAsState() ?: remember { mutableStateOf(null) }
    val errorMessage by agentLoop?.errorMessage?.collectAsState() ?: remember { mutableStateOf(null) }

    var taskInput by remember { mutableStateOf("") }
    val isRunning = state != AgentState.IDLE && state != AgentState.COMPLETED &&
            state != AgentState.CANCELLED && state != AgentState.ERROR &&
            state != AgentState.MAX_STEPS_REACHED

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Molmo Agent",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Row {
                    IconButton(onClick = onOpenApp, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.OpenInNew, "Open app", tint = Color.White.copy(alpha = 0.7f))
                    }
                }
            }

            // Status indicator
            if (isRunning) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF4FC3F7)
                    )
                    Text(
                        text = when (state) {
                            AgentState.OBSERVING -> "Observing screen..."
                            AgentState.REASONING -> "Thinking..."
                            AgentState.ACTING -> "Executing action..."
                            AgentState.PAUSED -> "Paused"
                            else -> state.name
                        },
                        color = Color(0xFF4FC3F7),
                        fontSize = 13.sp
                    )
                    currentStep?.let { step ->
                        Text(
                            "(Step ${step.stepNumber})",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                }

                // Current thought
                currentStep?.let { step ->
                    if (step.thought.isNotEmpty()) {
                        Text(
                            text = step.thought,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                                .padding(8.dp)
                        )
                    }
                }

                // Control buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state == AgentState.PAUSED) {
                        FilledTonalButton(
                            onClick = onResume,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF4CAF50)
                            )
                        ) {
                            Icon(Icons.Default.PlayArrow, "Resume", tint = Color.White)
                            Spacer(Modifier.width(4.dp))
                            Text("Resume", color = Color.White)
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onPause,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFFFFA726)
                            )
                        ) {
                            Icon(Icons.Default.Pause, "Pause", tint = Color.White)
                            Spacer(Modifier.width(4.dp))
                            Text("Pause", color = Color.White)
                        }
                    }

                    FilledTonalButton(
                        onClick = onStop,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFFF44336)
                        )
                    ) {
                        Icon(Icons.Default.Stop, "Stop", tint = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text("Stop", color = Color.White)
                    }
                }
            }

            // Completion / error messages
            completionMessage?.let { msg ->
                if (state == AgentState.COMPLETED) {
                    Text(
                        text = msg,
                        color = Color(0xFF4CAF50),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF4CAF50).copy(alpha = 0.1f))
                            .padding(8.dp)
                    )
                }
            }

            errorMessage?.let { msg ->
                if (state == AgentState.ERROR || state == AgentState.MAX_STEPS_REACHED) {
                    Text(
                        text = msg,
                        color = Color(0xFFF44336),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF44336).copy(alpha = 0.1f))
                            .padding(8.dp)
                    )
                }
            }

            // Input field (show when idle or finished)
            if (!isRunning) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = taskInput,
                        onValueChange = { taskInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("What should I do?", color = Color.White.copy(alpha = 0.4f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF4FC3F7),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            cursorColor = Color(0xFF4FC3F7)
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    FilledIconButton(
                        onClick = {
                            if (taskInput.isNotBlank()) {
                                onSubmitTask(taskInput.trim())
                                taskInput = ""
                            }
                        },
                        enabled = taskInput.isNotBlank(),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xFF4FC3F7)
                        )
                    ) {
                        Icon(Icons.Default.Send, "Send", tint = Color.White)
                    }
                }
            }
        }
    }
}
