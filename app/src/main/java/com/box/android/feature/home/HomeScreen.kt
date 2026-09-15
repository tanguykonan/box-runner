package com.box.android.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.box.android.R
import com.box.android.core.ui.avatar.UserAvatar
import com.box.android.data.box.BoxApp
import com.box.android.data.box.BoxAppStatus
import com.box.android.data.box.BoxRuntime
import androidx.compose.material3.ExperimentalMaterial3Api
import com.box.android.feature.home.components.BoxAppItem
import com.box.android.feature.home.components.NewItemBottomSheet
import com.box.android.feature.resources.ResourcesScreen
import com.box.android.feature.service.components.EnvConfigBottomSheet
import com.box.android.feature.settings.SettingsScreen
import com.box.android.ui.theme.BoxAndroidAppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToAppDetails: (String) -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    onNavigateToNewApp: (String?) -> Unit = {},
    onNavigateToNewVolume: () -> Unit = {},
    onNavigateToVolumes: () -> Unit = {},
    onNavigateToFaq: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is HomeEffect.NavigateToAppDetails -> onNavigateToAppDetails(effect.appId)
                is HomeEffect.NavigateToProfile -> onNavigateToProfile()
                is HomeEffect.NavigateToLogin -> onNavigateToLogin()
                is HomeEffect.NavigateToNewApp -> onNavigateToNewApp(effect.packageId)
                is HomeEffect.NavigateToNewVolume -> onNavigateToNewVolume()
                is HomeEffect.OpenBoxPicker -> {}
                is HomeEffect.ShowToast -> {
                    val msg = if (effect.message.startsWith("CACHE_CLEARED")) {
                        val freedBytes = effect.message.substringAfter(":", "").toLongOrNull() ?: 0L
                        if (freedBytes > 0L) {
                            val isFr = java.util.Locale.getDefault().language == "fr"
                            val formattedSize = when {
                                freedBytes >= 1024 * 1024 * 1024 -> String.format(java.util.Locale.getDefault(), "%.2f %s", freedBytes.toDouble() / (1024 * 1024 * 1024), if (isFr) "Go" else "GB")
                                freedBytes >= 1024 * 1024 -> String.format(java.util.Locale.getDefault(), "%.1f %s", freedBytes.toDouble() / (1024 * 1024), if (isFr) "Mo" else "MB")
                                freedBytes >= 1024 -> String.format(java.util.Locale.getDefault(), "%.0f %s", freedBytes.toDouble() / 1024, if (isFr) "Ko" else "KB")
                                else -> "$freedBytes ${if (isFr) "octets" else "bytes"}"
                            }
                            context.getString(R.string.settings_cache_cleared_with_size, formattedSize)
                        } else {
                            context.getString(R.string.settings_cache_already_empty)
                        }
                    } else {
                        effect.message
                    }
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    HomeContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateToVolumes = onNavigateToVolumes,
        onNavigateToFaq = onNavigateToFaq,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    uiState: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    onNavigateToVolumes: () -> Unit = {},
    onNavigateToFaq: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Full screen content based on active tab
        when (uiState.selectedTab) {
            HomeTab.RESOURCES -> {
                ResourcesScreen(uiState = uiState)
            }
            HomeTab.HOME -> {
                HomeAppsView(
                    uiState = uiState,
                    onEvent = onEvent
                )
            }
            HomeTab.SETTINGS -> {
                SettingsScreen(
                    uiState = uiState,
                    onEvent = onEvent,
                    onNavigateToVolumes = onNavigateToVolumes,
                    onNavigateToFaq = onNavigateToFaq
                )
            }
        }

        // Floating Action Button (FAB) floating nicely above the floating pill
        if (uiState.selectedTab == HomeTab.HOME) {
            val rustOrange = Color(0xFFE55B25)
            FloatingActionButton(
                onClick = { onEvent(HomeEvent.OpenNewItemSheet) },
                shape = CircleShape,
                containerColor = rustOrange,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 12.dp
                ),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = 86.dp)
                    .size(50.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Add item",
                        tint = rustOrange,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Telegram-Style Floating Pill Navigation Bar
        HomeTelegramBottomBar(
            selectedTab = uiState.selectedTab,
            onTabSelected = { onEvent(HomeEvent.SelectTab(it)) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
        )

        // Bottom Sheet : Nouvelle application / Nouveau volume
        if (uiState.isNewItemSheetVisible) {
            NewItemBottomSheet(
                onDismiss = { onEvent(HomeEvent.DismissNewItemSheet) },
                onNewAppClick = { onEvent(HomeEvent.NewAppSelected) },
                onNewVolumeClick = { onEvent(HomeEvent.NewVolumeSelected) }
            )
        }

        // Confirmation Dialog for App Deletion
        uiState.appPendingDeletion?.let { appToDelete ->
            AlertDialog(
                onDismissRequest = { onEvent(HomeEvent.DismissDeleteDialog) },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                title = {
                    Text(
                        text = stringResource(id = R.string.home_delete_confirm_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Text(
                        text = stringResource(id = R.string.home_delete_confirm_text, appToDelete.name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { onEvent(HomeEvent.ConfirmDeleteApp) },
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
                        onClick = { onEvent(HomeEvent.DismissDeleteDialog) }
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

        // Modal BottomSheet pour la configuration .env.local (Mode 2.C Hybride)
        uiState.appPendingEnvConfig?.let { appToConfig ->
            EnvConfigBottomSheet(
                app = appToConfig,
                onDismiss = { onEvent(HomeEvent.DismissEnvConfig) },
                onSave = { envVars, shouldStart ->
                    onEvent(HomeEvent.SaveEnvConfig(appToConfig.id, envVars, shouldStart))
                }
            )
        }
    }
}

@Composable
private fun HomeTelegramBottomBar(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .shadow(
                elevation = 14.dp,
                shape = CircleShape,
                spotColor = Color.Black.copy(alpha = 0.35f),
                ambientColor = Color.Black.copy(alpha = 0.15f)
            ),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeTab.entries.forEach { tab ->
                val isSelected = selectedTab == tab
                val activeColor = Color(0xFFE55B25)
                val tabTitle = stringResource(id = tab.titleRes)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(tab) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (isSelected) activeColor.copy(alpha = 0.14f) else Color.Transparent
                            )
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tabTitle,
                                tint = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = tabTitle,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp
                                ),
                                color = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeAppsView(
    uiState: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Top Header Section (One UI Style)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp)
        ) {
            // Search Bar with Pill/Capsule Shape & Soft Border
            AnimatedVisibility(
                visible = uiState.isSearchActive,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(200))
            ) {
                val focusRequester = remember { FocusRequester() }

                LaunchedEffect(uiState.isSearchActive) {
                    if (uiState.isSearchActive) {
                        focusRequester.requestFocus()
                    }
                }

                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { onEvent(HomeEvent.SearchQueryChanged(it)) },
                    placeholder = {
                        Text(
                            text = stringResource(id = R.string.home_search_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = stringResource(id = R.string.home_search_icon_desc),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    onEvent(HomeEvent.SearchQueryChanged(""))
                                } else {
                                    onEvent(HomeEvent.ToggleSearch)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(id = R.string.home_search_close_desc),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .focusRequester(focusRequester),
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        focusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            if (!uiState.isSearchActive) {
                // Large Title (Google Messages / One UI Style)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp, bottom = 24.dp),
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

                // Header Action Buttons (Search + Profile Avatar)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onEvent(HomeEvent.ToggleSearch) },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = stringResource(id = R.string.home_search_icon_desc),
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Logo de l'application (remplace la photo de profil)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0F172A))
                            .border(
                                width = 1.dp,
                                color = Color(0xFF1E293B),
                                shape = CircleShape
                            )
                            .clickable { onEvent(HomeEvent.ProfileClicked) },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = "Box Runner Logo",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    }
                }
            }
        }

        // Main Rounded Container (Always fills down to bottom of screen)
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
            if (uiState.filteredApps.isEmpty()) {
                if (uiState.searchQuery.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SearchOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = stringResource(id = R.string.home_empty_search_title),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    var packageRefInput by remember { mutableStateOf("") }
                    val rustOrange = Color(0xFFE55B25)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp)
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 28.dp, bottom = 90.dp)
                        ) {
                            // Icon container with theme background & orange border
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    .border(
                                        width = 1.5.dp,
                                        color = rustOrange,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudUpload,
                                    contentDescription = null,
                                    tint = rustOrange,
                                    modifier = Modifier.size(30.dp)
                                )
                            }

                            // Subtitle
                            Text(
                                text = stringResource(id = R.string.home_empty_title),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )

                            // Description
                            Text(
                                text = stringResource(id = R.string.home_empty_desc),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            // Input Field for Package Reference (Completely neutral border & background, no focus stroke shift)
                            BasicTextField(
                                value = packageRefInput,
                                onValueChange = { packageRefInput = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(rustOrange),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        focusManager.clearFocus()
                                        onEvent(HomeEvent.StartNewApp(packageRefInput.trim()))
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                            .border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                                                shape = RoundedCornerShape(14.dp)
                                            )
                                            .padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Dns,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Box(modifier = Modifier.weight(1f)) {
                                                if (packageRefInput.isEmpty()) {
                                                    Text(
                                                        text = stringResource(id = R.string.home_empty_ref_placeholder),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        }
                                    }
                                }
                            )

                            // Continue Button
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    onEvent(HomeEvent.StartNewApp(packageRefInput.trim()))
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = rustOrange,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.action_continue),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    )
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 12.dp)
                ) {
                    items(
                        items = uiState.filteredApps,
                        key = { it.id },
                        contentType = { "app_item" }
                    ) { app ->
                        val isStopped = app.status == BoxAppStatus.STOPPED
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart && isStopped) {
                                    onEvent(HomeEvent.DeleteApp(app.id))
                                    true
                                } else {
                                    false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            enableDismissFromEndToStart = isStopped,
                            modifier = Modifier.animateItem(),
                            backgroundContent = {
                                val isEndToStart = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
                                val backgroundColor = if (isEndToStart && isStopped) {
                                    Color(0xFFDC2626).copy(alpha = 0.9f)
                                } else {
                                    Color.Transparent
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(backgroundColor)
                                        .padding(horizontal = 24.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    if (isEndToStart && isStopped) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = stringResource(id = R.string.action_delete),
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = Color.White
                                            )
                                            Icon(
                                                imageVector = Icons.Rounded.Delete,
                                                contentDescription = stringResource(id = R.string.action_delete),
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        ) {
                            BoxAppItem(
                                app = app,
                                onClick = { onEvent(HomeEvent.AppClicked(app.id)) },
                                onToggleStatus = { onEvent(HomeEvent.ToggleAppStatus(app.id)) }
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                        )
                    }

                    // Bottom padding to ensure items scroll completely above the floating pill
                    item {
                        Spacer(modifier = Modifier.height(110.dp))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
fun HomePreviewLight() {
    BoxAndroidAppTheme(darkTheme = false) {
        HomeContent(
            uiState = HomeUiState(
                apps = listOf(
                    BoxApp(id = "1", name = "FastAPI Server", runtime = BoxRuntime.PYTHON, status = BoxAppStatus.RUNNING, description = "API", port = 8000, packageId = "box/fastapi", memoryUsageMb = 24.5f, uptime = "4h 12m", lastActivity = "Actif"),
                    BoxApp(id = "2", name = "Discord Bot", runtime = BoxRuntime.NODEJS, status = BoxAppStatus.RUNNING, description = "Bot", port = null, packageId = "box/discord", memoryUsageMb = 38.2f, uptime = "24h", lastActivity = "Actif"),
                    BoxApp(id = "3", name = "Telegram Assistant", runtime = BoxRuntime.PYTHON, status = BoxAppStatus.STOPPED, description = "AI", port = null, packageId = "box/telegram", memoryUsageMb = 0f, uptime = "0m", lastActivity = "Hier")
                ),
                filteredApps = listOf(
                    BoxApp(id = "1", name = "FastAPI Server", runtime = BoxRuntime.PYTHON, status = BoxAppStatus.RUNNING, description = "API", port = 8000, packageId = "box/fastapi", memoryUsageMb = 24.5f, uptime = "4h 12m", lastActivity = "Actif"),
                    BoxApp(id = "2", name = "Discord Bot", runtime = BoxRuntime.NODEJS, status = BoxAppStatus.RUNNING, description = "Bot", port = null, packageId = "box/discord", memoryUsageMb = 38.2f, uptime = "24h", lastActivity = "Actif"),
                    BoxApp(id = "3", name = "Telegram Assistant", runtime = BoxRuntime.PYTHON, status = BoxAppStatus.STOPPED, description = "AI", port = null, packageId = "box/telegram", memoryUsageMb = 0f, uptime = "0m", lastActivity = "Hier")
                )
            ),
            onEvent = {}
        )
    }
}
