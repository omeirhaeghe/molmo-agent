package com.example.molmoagent.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.molmoagent.agent.AgentState

private val FabGradient = Brush.verticalGradient(
    listOf(Color(0xFF1A2440), Color(0xFF0D1228))
)
private val FabBorderBrush = Brush.verticalGradient(
    listOf(Color(0x884FC3F7), Color(0x2269F0AE))
)

@Composable
fun FloatingFab(agentState: AgentState) {
    val dotColor = when (agentState) {
        AgentState.OBSERVING, AgentState.REASONING, AgentState.ACTING ->
            Color(0xFF4FC3F7)
        AgentState.PAUSED ->
            Color(0xFFFFCA28)
        AgentState.COMPLETED ->
            Color(0xFF69F0AE)
        AgentState.ERROR, AgentState.MAX_STEPS_REACHED ->
            Color(0xFFFF5252)
        else ->
            Color(0x55FFFFFF)
    }

    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(FabGradient)
            .border(1.dp, FabBorderBrush, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "✦",
            color = Color(0xFF4FC3F7),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        // Status dot with dark ring for visibility on any background
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(5.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(0xFF0D1228))
                .padding(2.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
    }
}
