package com.box.android.feature.service.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.box.android.R
import com.box.android.data.box.BoxApp
import com.box.android.data.box.BoxAppStatus
import com.box.android.feature.service.components.EnvConfigBottomSheet

@Composable
fun AppDetailsScreen(
    viewModel: AppDetailsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is AppDetailsEffect.NavigateBack -> onBack()
            }
        }
    }

    val app = uiState.app

    if (app == null) {
        Box(
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(id = R.string.resources_live_badge), color = MaterialTheme.colorScheme.onBackground)
        }
        return
    }

    AppDetailsContent(
        app = app,
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier
    )

    if (uiState.isEnvSheetVisible) {
        EnvConfigBottomSheet(
            app = app,
            onDismiss = { viewModel.onEvent(AppDetailsEvent.DismissEnvSheet) },
            onSave = { envVars, shouldStart ->
                viewModel.onEvent(AppDetailsEvent.SaveEnvVars(envVars, shouldStart))
            }
        )
    }
}

@Composable
fun AppDetailsContent(
    app: BoxApp,
    uiState: AppDetailsUiState,
    onEvent: (AppDetailsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val isRunning = app.status == BoxAppStatus.RUNNING
    val isStarting = app.status == BoxAppStatus.STARTING
    val telegramBlue = Color(0xFF2AABEE)
    val rustOrange = Color(0xFFE55B25)
    val warningYellow = Color(0xFFEAB308)
    val mbUnit = stringResource(id = R.string.unit_mb)
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Header : Back + Title + Delete Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(id = R.string.action_back),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                Text(
                    text = stringResource(id = R.string.details_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                IconButton(
                    onClick = { onEvent(AppDetailsEvent.RequestDeleteApp) },
                    enabled = !isRunning && !isStarting,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(id = if (isRunning || isStarting) R.string.details_delete_disabled_tooltip else R.string.details_delete_service_tooltip),
                        tint = if (!isRunning && !isStarting) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                }
            }

            // Contenu scrollable
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. CARTE IDENTITÉ & RENOMMAGE
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Nom modifiable
                        if (uiState.isEditingName) {
                            OutlinedTextField(
                                value = uiState.editedName,
                                onValueChange = { onEvent(AppDetailsEvent.NameChanged(it)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                trailingIcon = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(end = 6.dp)
                                    ) {
                                        // Save (Solid orange pill 30dp)
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .background(rustOrange)
                                                .clickable { onEvent(AppDetailsEvent.SaveName) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = stringResource(id = R.string.action_save),
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // Cancel (Solid neutral pill 30dp)
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { onEvent(AppDetailsEvent.CancelEditingName) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = stringResource(id = R.string.action_cancel),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = rustOrange,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                )
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = app.name,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(
                                    onClick = { onEvent(AppDetailsEvent.StartEditingName) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = stringResource(id = R.string.details_edit_name_tooltip),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Package Reference
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Dns,
                                contentDescription = null,
                                tint = rustOrange,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = app.packageId.ifBlank { "box/service:latest" },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 2. Package Information Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = stringResource(id = R.string.details_section_package_info),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val noneText = stringResource(id = R.string.details_metric_port_none)
                        val stoppedText = stringResource(id = R.string.app_status_stopped)
                        val downloadingText = stringResource(id = R.string.details_btn_downloading)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricItem(
                                icon = Icons.Rounded.Terminal,
                                iconColor = Color(0xFF8B5CF6),
                                label = stringResource(id = R.string.details_metric_environment),
                                value = app.runtime.displayName,
                                modifier = Modifier.weight(1f)
                            )
                            MetricItem(
                                icon = Icons.Rounded.Lan,
                                iconColor = Color(0xFF2AABEE),
                                label = stringResource(id = R.string.details_metric_network_port),
                                value = app.port?.toString() ?: noneText,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricItem(
                                icon = Icons.Rounded.Memory,
                                iconColor = Color(0xFF10B981),
                                label = stringResource(id = R.string.details_metric_ram_memory),
                                value = if (isRunning) "%.1f %s".format(app.memoryUsageMb, mbUnit) else "0 $mbUnit",
                                modifier = Modifier.weight(1f)
                            )
                            MetricItem(
                                icon = Icons.Rounded.Schedule,
                                iconColor = Color(0xFFF59E0B),
                                label = stringResource(id = R.string.details_metric_activity),
                                value = when (app.status) {
                                    BoxAppStatus.RUNNING -> app.uptime
                                    BoxAppStatus.STARTING -> downloadingText
                                    else -> stoppedText
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 3. Variables .env.local Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(id = R.string.details_section_env),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (app.requiresConfig) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFDC2626))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.details_env_not_configured),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        ),
                                        color = Color.White
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF10B981))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.details_env_configured_count, app.envVars.size),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        ),
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { onEvent(AppDetailsEvent.OpenEnvSheet) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = rustOrange
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(id = R.string.details_btn_manage_env),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // 4. Live Output & Logs Console Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (app.status) {
                                                BoxAppStatus.RUNNING -> Color(0xFF10B981)
                                                BoxAppStatus.STARTING -> warningYellow
                                                else -> Color(0xFF64748B)
                                            }
                                        )
                                )
                                Text(
                                    text = stringResource(id = R.string.details_section_logs),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (uiState.logs.isNotEmpty()) {
                                    val logsCopiedToast = stringResource(id = R.string.details_logs_copied)
                                    val copyLogsDesc = stringResource(id = R.string.details_copy_logs_desc)
                                    IconButton(
                                        onClick = {
                                            val fullLogs = uiState.logs.joinToString("\n") { it.message }
                                            clipboardManager.setText(AnnotatedString(fullLogs))
                                            Toast.makeText(context, logsCopiedToast, Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ContentCopy,
                                            contentDescription = copyLogsDesc,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }

                                val statusBadgeText = when (app.status) {
                                    BoxAppStatus.RUNNING -> "LIVE"
                                    BoxAppStatus.STARTING -> "STARTING"
                                    else -> "OFFLINE"
                                }
                                val statusBadgeColor = when (app.status) {
                                    BoxAppStatus.RUNNING -> Color(0xFF10B981)
                                    BoxAppStatus.STARTING -> warningYellow
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                }
                                Text(
                                    text = statusBadgeText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    color = statusBadgeColor
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0B0F17))
                                .border(
                                    width = 1.dp,
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp)
                        ) {
                            if (uiState.logs.isEmpty()) {
                                if (isRunning) {
                                    Text(
                                        text = stringResource(id = R.string.details_logs_empty_running),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        ),
                                        color = Color(0xFF64748B)
                                    )
                                }
                            } else {
                                val logsScroll = rememberScrollState()
                                LaunchedEffect(uiState.logs.size) {
                                    logsScroll.animateScrollTo(logsScroll.maxValue)
                                }
                                SelectionContainer {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(logsScroll),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        uiState.logs.forEach { log ->
                                            val lineColor = when {
                                                log.isError || log.message.startsWith("[ERROR]") -> Color(0xFFF87171)
                                                log.message.startsWith("[SYSTEM]") -> Color(0xFF94A3B8)
                                                log.message.startsWith("[RUNTIME]") -> Color(0xFFA78BFA)
                                                log.message.startsWith("[RUNNER]") -> Color(0xFF38BDF8)
                                                log.message.startsWith("[READY]") -> Color(0xFF34D399)
                                                log.message.startsWith("[INFO]") -> Color(0xFF60A5FA)
                                                log.message.startsWith("[WARN]") -> Color(0xFFFBBF24)
                                                else -> Color(0xFFE2E8F0)
                                            }

                                            Text(
                                                text = log.message,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp,
                                                    lineHeight = 16.sp
                                                ),
                                                color = lineColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Main Start / Stop Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                val buttonBg = when {
                    isRunning -> rustOrange
                    isStarting -> warningYellow
                    else -> telegramBlue
                }
                val buttonText = when {
                    isRunning -> stringResource(id = R.string.details_btn_stop)
                    isStarting -> stringResource(id = R.string.details_btn_downloading)
                    else -> stringResource(id = R.string.details_btn_start)
                }
                val buttonIcon = when {
                    isRunning -> Icons.Rounded.Stop
                    isStarting -> Icons.Rounded.Download
                    else -> Icons.Rounded.PlayArrow
                }

                Button(
                    onClick = { onEvent(AppDetailsEvent.ToggleStatus) },
                    enabled = !isStarting,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonBg,
                        contentColor = Color.White,
                        disabledContainerColor = if (isStarting) warningYellow else MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = if (isStarting) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(
                        imageVector = buttonIcon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = buttonText,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    )
                }
            }

            // Deletion confirmation dialog
            if (uiState.isDeleteDialogOpen) {
                AlertDialog(
                    onDismissRequest = { onEvent(AppDetailsEvent.DismissDeleteDialog) },
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = {
                        Text(
                            text = stringResource(id = R.string.details_delete_confirm_title),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(id = R.string.details_delete_confirm_text, app.name),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = { onEvent(AppDetailsEvent.ConfirmDeleteApp) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFDC2626),
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = stringResource(id = R.string.action_delete),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { onEvent(AppDetailsEvent.DismissDeleteDialog) }
                        ) {
                            Text(
                                text = stringResource(id = R.string.action_cancel),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun MetricItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(iconColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
