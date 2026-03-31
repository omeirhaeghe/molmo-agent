package com.example.molmoagent.ui.setup

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.molmoagent.accessibility.AgentAccessibilityService

@Composable
fun SetupScreen(
    onAllPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current

    // Re-check permissions periodically when screen is visible
    var checkTrigger by remember { mutableIntStateOf(0) }

    val accessibilityEnabled = remember(checkTrigger) {
        AgentAccessibilityService.isRunning()
    }
    val overlayEnabled = remember(checkTrigger) {
        Settings.canDrawOverlays(context)
    }

    // Refresh checks when returning to the app
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1000)
        checkTrigger++
    }

    val allGranted = accessibilityEnabled && overlayEnabled

    LaunchedEffect(allGranted) {
        if (allGranted) {
            onAllPermissionsGranted()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Setup",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Molmo Agent needs a few permissions to automate your phone.",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Accessibility Service
        PermissionCard(
            icon = Icons.Default.Accessibility,
            title = "Accessibility Service",
            description = "Required for taking screenshots, tapping, and typing on your behalf.",
            isGranted = accessibilityEnabled,
            onGrant = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onRefresh = { checkTrigger++ }
        )

        // Overlay Permission
        PermissionCard(
            icon = Icons.Default.Layers,
            title = "Display Over Other Apps",
            description = "Shows the floating agent panel on top of other apps.",
            isGranted = overlayEnabled,
            onGrant = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${context.packageName}")
                    )
                )
            },
            onRefresh = { checkTrigger++ }
        )

        Spacer(modifier = Modifier.weight(1f))

        if (allGranted) {
            Button(
                onClick = onAllPermissionsGranted,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Continue")
            }
        } else {
            OutlinedButton(
                onClick = { checkTrigger++ },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Refresh Permissions")
            }
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    onGrant: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted)
                Color(0xFF1B5E20).copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isGranted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            if (isGranted) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Granted",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Button(
                    onClick = onGrant,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Grant", fontSize = 13.sp)
                }
            }
        }
    }
}
