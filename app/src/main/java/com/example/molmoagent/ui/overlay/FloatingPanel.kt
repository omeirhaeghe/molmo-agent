package com.example.molmoagent.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.molmoagent.agent.AgentState

// Glass palette
private val GlassBg = Brush.verticalGradient(
    listOf(Color(0xD01C1C2C), Color(0xC8100E1E))
)
private val GlassBorder = Brush.verticalGradient(
    listOf(Color(0x50FFFFFF), Color(0x18FFFFFF))
)
private val AccentCyan   = Color(0xFF4FC3F7)
private val AccentGreen  = Color(0xFF69F0AE)
private val AccentAmber  = Color(0xFFFFCA28)
private val AccentRed    = Color(0xFFFF5252)
private val TextPrimary  = Color(0xFFFFFFFF)
private val TextMuted    = Color(0x99FFFFFF)
private val SurfaceTint  = Color(0x14FFFFFF)
// Input field gets a more opaque background so it reads clearly
private val InputBg      = Color(0x55FFFFFF)

@Composable
fun FloatingPanel(
    onSubmitTask: (String) -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onOpenApp: () -> Unit,
    onDismiss: () -> Unit = {},
    onInputFocused: (Boolean) -> Unit = {}
) {
    val agentLoop = AgentLoopHolder.agentLoop
    val state by agentLoop?.state?.collectAsState() ?: remember { mutableStateOf(AgentState.IDLE) }
    val currentStep by agentLoop?.currentStep?.collectAsState() ?: remember { mutableStateOf(null) }
    val completionMessage by agentLoop?.completionMessage?.collectAsState() ?: remember { mutableStateOf(null) }
    val errorMessage by agentLoop?.errorMessage?.collectAsState() ?: remember { mutableStateOf(null) }

    var taskInput by remember { mutableStateOf("") }
    var dragAccum by remember { mutableFloatStateOf(0f) }

    val isRunning = state != AgentState.IDLE && state != AgentState.COMPLETED &&
            state != AgentState.CANCELLED && state != AgentState.ERROR &&
            state != AgentState.MAX_STEPS_REACHED

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize()
            .clip(RoundedCornerShape(24.dp))
            .background(GlassBg)
            .border(0.5.dp, GlassBorder, RoundedCornerShape(24.dp))
    ) {
        Column {
            // ── Drag handle ────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragAccum > 80f) onDismiss()
                                dragAccum = 0f
                            },
                            onDragCancel = { dragAccum = 0f }
                        ) { _, amount ->
                            dragAccum += amount
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(TextMuted.copy(alpha = 0.35f))
                )
            }

            // ── Content ────────────────────────────────────────────────────
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                // ── Running state ───────────────────────────────────────────
                AnimatedVisibility(visible = isRunning, enter = fadeIn(), exit = fadeOut()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = AccentCyan
                                )
                                Text(
                                    text = when (state) {
                                        AgentState.OBSERVING -> "Observing…"
                                        AgentState.REASONING -> "Thinking…"
                                        AgentState.ACTING    -> "Acting…"
                                        AgentState.PAUSED    -> "Paused"
                                        else -> state.name
                                    },
                                    color = AccentCyan,
                                    fontSize = 13.sp,
                                    letterSpacing = 0.3.sp
                                )
                                currentStep?.let {
                                    Text(
                                        "· step ${it.stepNumber}",
                                        color = TextMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (state == AgentState.PAUSED) {
                                    GlassIconButton(onClick = onResume, tint = AccentGreen) {
                                        Icon(Icons.Default.PlayArrow, "Resume", Modifier.size(18.dp))
                                    }
                                } else {
                                    GlassIconButton(onClick = onPause, tint = AccentAmber) {
                                        Icon(Icons.Default.Pause, "Pause", Modifier.size(18.dp))
                                    }
                                }
                                GlassIconButton(onClick = onStop, tint = AccentRed) {
                                    Icon(Icons.Default.Stop, "Stop", Modifier.size(18.dp))
                                }
                            }
                        }

                        currentStep?.let { step ->
                            if (step.thought.isNotEmpty()) {
                                Text(
                                    text = step.thought,
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 17.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SurfaceTint)
                                        .padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }

                // ── Completion ──────────────────────────────────────────────
                AnimatedVisibility(
                    visible = state == AgentState.COMPLETED && completionMessage != null,
                    enter = fadeIn(), exit = fadeOut()
                ) {
                    completionMessage?.let { msg ->
                        Text(
                            text = "✓ $msg",
                            color = AccentGreen,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(AccentGreen.copy(alpha = 0.08f))
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }

                // ── Error ───────────────────────────────────────────────────
                AnimatedVisibility(
                    visible = (state == AgentState.ERROR || state == AgentState.MAX_STEPS_REACHED)
                            && errorMessage != null,
                    enter = fadeIn(), exit = fadeOut()
                ) {
                    errorMessage?.let { msg ->
                        Text(
                            text = msg,
                            color = AccentRed,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(AccentRed.copy(alpha = 0.08f))
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }

                // ── Input row ───────────────────────────────────────────────
                if (!isRunning) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = taskInput,
                            onValueChange = { taskInput = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = TextPrimary,
                                fontSize = 15.sp
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { onInputFocused(it.isFocused) },
                            decorationBox = { inner ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(InputBg)
                                        .padding(horizontal = 14.dp, vertical = 11.dp)
                                ) {
                                    if (taskInput.isEmpty()) {
                                        Text(
                                            "What should I do?",
                                            color = TextMuted,
                                            fontSize = 15.sp
                                        )
                                    }
                                    inner()
                                }
                            }
                        )

                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    if (taskInput.isNotBlank()) AccentCyan else SurfaceTint
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = {
                                    if (taskInput.isNotBlank()) {
                                        onSubmitTask(taskInput.trim())
                                        taskInput = ""
                                    }
                                },
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(
                                    Icons.Default.Send,
                                    "Send",
                                    tint = if (taskInput.isNotBlank()) Color.White else TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassIconButton(
    onClick: () -> Unit,
    tint: Color,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(SurfaceTint),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
            CompositionLocalProvider(LocalContentColor provides tint) {
                content()
            }
        }
    }
}
