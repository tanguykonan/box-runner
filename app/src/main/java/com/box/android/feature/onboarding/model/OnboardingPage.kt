package com.box.android.feature.onboarding.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@Immutable
data class OnboardingPage(
    val badge: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val icon: ImageVector,
    val iconColor: Color
)

object OnboardingPages {
    val list = listOf(
        OnboardingPage(
            badge = "STANDALONE",
            title = "Run your applications",
            subtitle = "Python, Node.js",
            description = "A single .box file contains your code, runtime, and dependencies with zero configuration required.",
            icon = Icons.Rounded.Terminal,
            iconColor = Color(0xFF1D4ED8)
        ),
        OnboardingPage(
            badge = "BACKGROUND",
            title = "Mobile Self-Hosting",
            subtitle = "Low-power native background service",
            description = "Your bots and APIs stay active 24/7 with optimized system resource management.",
            icon = Icons.Rounded.AllInclusive,
            iconColor = Color(0xFF15803D)
        ),
        OnboardingPage(
            badge = "MONITORING",
            title = "Resource Monitoring",
            subtitle = "System metrics tracking",
            description = "Track live CPU usage, RAM memory, logs, and application health in real time.",
            icon = Icons.Rounded.Memory,
            iconColor = Color(0xFFD97706)
        )
    )
}
