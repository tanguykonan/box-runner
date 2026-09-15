package com.box.android.feature.resources

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.box.android.R
import com.box.android.data.box.BoxAppStatus
import com.box.android.feature.home.HomeUiState

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.box.android.core.system.SystemStatsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun ResourcesScreen(
    uiState: HomeUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val statsManager = remember(context) { SystemStatsManager(context.applicationContext) }
    val hardwareSpecs = remember(statsManager) { statsManager.getHardwareSpecs() }

    val runningApps = remember(uiState.apps) {
        uiState.apps.filter { it.status == BoxAppStatus.RUNNING }
    }
    val totalMemoryUsedMb = remember(runningApps) {
        runningApps.sumOf { it.memoryUsageMb.toDouble() }.toFloat()
    }

    var liveStats by remember {
        mutableStateOf(
            statsManager.getLiveStats(
                runningAppsCount = runningApps.size,
                runningAppsMemoryMb = totalMemoryUsedMb,
                previousWaveform = emptyList()
            )
        )
    }

    LaunchedEffect(runningApps.size, totalMemoryUsedMb) {
        while (isActive) {
            liveStats = statsManager.getLiveStats(
                runningAppsCount = runningApps.size,
                runningAppsMemoryMb = totalMemoryUsedMb,
                previousWaveform = liveStats.waveformPoints
            )
            delay(2500L)
        }
    }

    val mbUnit = stringResource(id = R.string.unit_mb)
    val gbUnit = stringResource(id = R.string.unit_gb)

    val formattedRamUsed = "%.1f %s".format(liveStats.ramUsedMb, mbUnit)
    val formattedRomUsed = if (liveStats.romUsedMb >= 1024f) {
        "%.1f %s".format(liveStats.romUsedMb / 1024f, gbUnit)
    } else {
        "%.1f %s".format(liveStats.romUsedMb, mbUnit)
    }

    val batterySubtitle = if (liveStats.isCharging) {
        stringResource(id = R.string.resources_spec_battery_charging)
    } else {
        hardwareSpecs.batteryCapacityMah
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header One UI
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        fontSize = 34.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Surface
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                )
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Consumption section
                    item {
                        Text(
                            text = stringResource(id = R.string.resources_consumption_title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    item {
                        ConsumptionGraphCard(
                            cpu = "${liveStats.cpuPercentage}%",
                            ram = formattedRamUsed,
                            rom = formattedRomUsed,
                            battery = "${liveStats.batteryPercentage}%",
                            waveformPoints = liveStats.waveformPoints
                        )
                    }

                    // Hardware specifications section
                    item {
                        Text(
                            text = stringResource(id = R.string.resources_specs_title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    // 4 Grid cards (2x2)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SpecGridCard(
                                title = stringResource(id = R.string.resources_metric_cpu),
                                value = hardwareSpecs.cpuAbi,
                                subtitle = stringResource(id = R.string.resources_spec_cpu_cores, hardwareSpecs.cpuCores),
                                icon = Icons.Rounded.Speed,
                                iconColor = Color(0xFF2AABEE),
                                modifier = Modifier.weight(1f)
                            )
                            SpecGridCard(
                                title = stringResource(id = R.string.resources_metric_ram),
                                value = "${hardwareSpecs.totalRamGb} $gbUnit",
                                subtitle = stringResource(
                                    id = R.string.resources_spec_free_space,
                                    "%.1f %s".format(hardwareSpecs.availableRamGb, gbUnit)
                                ),
                                icon = Icons.Rounded.Memory,
                                iconColor = Color(0xFF10B981),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SpecGridCard(
                                title = stringResource(id = R.string.resources_metric_rom),
                                value = "${hardwareSpecs.totalStorageGb} $gbUnit",
                                subtitle = stringResource(
                                    id = R.string.resources_spec_free_space,
                                    "${hardwareSpecs.availableStorageGb} $gbUnit"
                                ),
                                icon = Icons.Rounded.SdCard,
                                iconColor = Color(0xFF8B5CF6),
                                modifier = Modifier.weight(1f)
                            )
                            SpecGridCard(
                                title = stringResource(id = R.string.resources_metric_battery),
                                value = "${liveStats.batteryPercentage}%",
                                subtitle = batterySubtitle,
                                icon = Icons.Rounded.BatteryStd,
                                iconColor = Color(0xFFF59E0B),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(110.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsumptionGraphCard(
    cpu: String,
    ram: String,
    rom: String,
    battery: String,
    waveformPoints: List<Float> = emptyList(),
    modifier: Modifier = Modifier
) {
    val graphColor = Color(0xFF10B981)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Top Graph Header with live indicator
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
                            .background(graphColor)
                    )
                    Text(
                        text = stringResource(id = R.string.resources_activity_title),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = stringResource(id = R.string.resources_live_badge),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Smooth Real-Time Vector Waveform Chart
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
            ) {
                val width = size.width
                val height = size.height

                // Points along time axis
                val points = if (waveformPoints.size >= 2) {
                    waveformPoints.mapIndexed { index, normValue ->
                        val x = (width / (waveformPoints.size - 1)) * index
                        val y = height * (1f - normValue.coerceIn(0.12f, 0.88f))
                        Offset(x, y)
                    }
                } else {
                    listOf(
                        Offset(0f, height * 0.75f),
                        Offset(width * 0.15f, height * 0.65f),
                        Offset(width * 0.30f, height * 0.80f),
                        Offset(width * 0.45f, height * 0.40f),
                        Offset(width * 0.60f, height * 0.55f),
                        Offset(width * 0.75f, height * 0.30f),
                        Offset(width * 0.90f, height * 0.45f),
                        Offset(width, height * 0.25f)
                    )
                }

                val strokePath = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 0 until points.size - 1) {
                        val p0 = points[i]
                        val p1 = points[i + 1]
                        val cx = (p0.x + p1.x) / 2
                        cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                    }
                }

                drawPath(
                    path = strokePath,
                    color = graphColor,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                drawCircle(
                    color = graphColor,
                    radius = 4.dp.toPx(),
                    center = points.last()
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
            )

            // 4 Metrics Grid within the SAME Block
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricColumn(label = stringResource(id = R.string.resources_metric_cpu), value = cpu)
                MetricColumn(label = stringResource(id = R.string.resources_metric_ram), value = ram)
                MetricColumn(label = stringResource(id = R.string.resources_metric_rom), value = rom)
                MetricColumn(label = stringResource(id = R.string.resources_metric_battery), value = battery)
            }
        }
    }
}

@Composable
private fun MetricColumn(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SpecGridCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
            }

            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
