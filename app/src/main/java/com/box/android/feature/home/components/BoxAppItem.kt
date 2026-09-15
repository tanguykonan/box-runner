package com.box.android.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.box.android.R
import com.box.android.data.box.BoxApp
import com.box.android.data.box.BoxAppStatus

@Composable
fun BoxAppItem(
    app: BoxApp,
    onClick: () -> Unit,
    onToggleStatus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRunning = app.status == BoxAppStatus.RUNNING
    val isStarting = app.status == BoxAppStatus.STARTING

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading Avatar with Pure Solid Badge + Status Dot
        Box(
            modifier = Modifier.size(46.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFE55B25)), // Pure Rust Orange
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Widgets,
                    contentDescription = app.name,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Live status dot
            val dotColor = when (app.status) {
                BoxAppStatus.RUNNING -> Color(0xFF10B981)
                BoxAppStatus.STARTING -> Color(0xFFEAB308)
                else -> Color(0xFF94A3B8)
            }

            Box(
                modifier = Modifier
                    .size(12.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(dotColor)
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.surface,
                        shape = CircleShape
                    )
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Center Content (Title + Subtitle, No Port badge)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = app.name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            val mbUnit = stringResource(id = R.string.unit_mb)
            val inactiveStatus = stringResource(id = R.string.app_status_inactive)
            val downloadingStatus = stringResource(id = R.string.details_btn_downloading)
            val detailsText = when (app.status) {
                BoxAppStatus.RUNNING -> "${app.runtime.displayName} • ${String.format("%.1f", app.memoryUsageMb)} $mbUnit • ${app.uptime}"
                BoxAppStatus.STARTING -> "${app.runtime.displayName} • $downloadingStatus"
                else -> "${app.runtime.displayName} • $inactiveStatus"
            }

            Text(
                text = detailsText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Trailing status / toggle action
        val telegramBlue = Color(0xFF2AABEE)
        val actionButtonBg = when {
            isRunning -> telegramBlue
            isStarting -> Color(0xFFEAB308)
            else -> telegramBlue
        }

        val actionIcon = when {
            isRunning -> Icons.Rounded.Stop
            isStarting -> Icons.Rounded.Download
            else -> Icons.Rounded.PlayArrow
        }
        val actionContentDescription = when {
            isRunning -> stringResource(id = R.string.action_stop)
            isStarting -> stringResource(id = R.string.details_btn_downloading)
            else -> stringResource(id = R.string.action_start)
        }

        IconButton(
            onClick = onToggleStatus,
            enabled = !isStarting,
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(actionButtonBg)
        ) {
            Icon(
                imageVector = actionIcon,
                contentDescription = actionContentDescription,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
