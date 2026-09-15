package com.box.android.feature.service.components

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.box.android.R
import com.box.android.data.box.BoxApp

enum class EnvEditMode(@StringRes val labelRes: Int) {
    KEY_VALUE(R.string.env_tab_key_value),
    RAW_TEXT(R.string.env_tab_raw)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvConfigBottomSheet(
    app: BoxApp,
    onDismiss: () -> Unit,
    onSave: (envVars: Map<String, String>, shouldStart: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val rustOrange = Color(0xFFE55B25)

    // Pairs initialization
    val envMap = remember {
        mutableStateMapOf<String, String>().apply {
            putAll(app.envVars.filterKeys { it != "raw" && it.isNotBlank() })
            app.requiredEnvKeys.filter { it != "raw" && it.isNotBlank() }.forEach { key ->
                if (!containsKey(key)) {
                    put(key, "")
                }
            }
        }
    }

    var editMode by remember { mutableStateOf(EnvEditMode.KEY_VALUE) }
    var rawText by remember {
        mutableStateOf(
            envMap.entries.joinToString("\n") { (k, v) -> "$k=$v" }
        )
    }

    var newKeyInput by remember { mutableStateOf("") }
    var newValueInput by remember { mutableStateOf("") }
    var isAddingNewPair by remember { mutableStateOf(false) }
    var isValidationAttempted by remember { mutableStateOf(false) }

    val visibilityMap = remember { mutableStateMapOf<String, Boolean>() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 42.dp, height = 4.5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f))
                )
            }
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.env_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(id = R.string.action_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Mode Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EnvEditMode.entries.forEach { mode ->
                    val isSelected = editMode == mode
                    val modeLabel = stringResource(id = mode.labelRes)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (isSelected) rustOrange else Color.Transparent
                            )
                            .clickable {
                                if (mode == EnvEditMode.RAW_TEXT) {
                                    rawText = envMap.entries.joinToString("\n") { (k, v) -> "$k=$v" }
                                } else {
                                    val parsed = rawText.lines()
                                        .filter { it.contains("=") && !it.trim().startsWith("#") }
                                        .associate {
                                            val key = it.substringBefore("=").trim()
                                            val value = it.substringAfter("=").trim()
                                            key to value
                                        }
                                        .filterKeys { it != "raw" && it.isNotBlank() }
                                    envMap.clear()
                                    envMap.putAll(parsed)
                                }
                                editMode = mode
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (mode == EnvEditMode.KEY_VALUE) Icons.Rounded.Tune else Icons.Rounded.Code,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = modeLabel,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                ),
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Mode Content
            if (editMode == EnvEditMode.KEY_VALUE) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (envMap.isEmpty() && !isAddingNewPair) {
                        Text(
                            text = stringResource(id = R.string.env_empty_text),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }

                    envMap.keys.toList().forEach { key ->
                        val value = envMap[key] ?: ""
                        val isSecretField = key.contains("TOKEN", ignoreCase = true) ||
                                key.contains("KEY", ignoreCase = true) ||
                                key.contains("SECRET", ignoreCase = true) ||
                                key.contains("PASSWORD", ignoreCase = true)
                        val isVisible = visibilityMap[key] ?: (!isSecretField)
                        val isFieldError = isValidationAttempted && value.isBlank()

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .border(
                                    width = 1.dp,
                                    color = if (isFieldError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Key,
                                            contentDescription = null,
                                            tint = if (isFieldError) MaterialTheme.colorScheme.error else rustOrange,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = key,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    IconButton(
                                        onClick = { envMap.remove(key) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(id = R.string.action_delete),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = value,
                                    onValueChange = { envMap[key] = it },
                                    placeholder = {
                                        Text(
                                            text = stringResource(id = R.string.env_placeholder_value_for, key),
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    },
                                    singleLine = true,
                                    isError = isFieldError,
                                    visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { visibilityMap[key] = !isVisible },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                contentDescription = stringResource(id = if (isVisible) R.string.env_hide_value else R.string.env_show_value),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = rustOrange,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                        errorBorderColor = MaterialTheme.colorScheme.error
                                    )
                                )

                                if (isFieldError) {
                                    Text(
                                        text = stringResource(id = R.string.env_err_field),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Add New Variable Form
                    if (isAddingNewPair) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .border(
                                    width = 1.dp,
                                    color = rustOrange.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = newKeyInput,
                                    onValueChange = { newKeyInput = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '_' } },
                                    placeholder = { Text(stringResource(id = R.string.env_new_key_placeholder), fontSize = 13.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = rustOrange,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                    )
                                )

                                OutlinedTextField(
                                    value = newValueInput,
                                    onValueChange = { newValueInput = it },
                                    placeholder = { Text(stringResource(id = R.string.env_new_val_placeholder), fontSize = 13.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = rustOrange,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                    )
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            isAddingNewPair = false
                                            newKeyInput = ""
                                            newValueInput = ""
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(stringResource(id = R.string.action_cancel), fontSize = 12.sp)
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = {
                                            if (newKeyInput.isNotBlank()) {
                                                envMap[newKeyInput.trim()] = newValueInput.trim()
                                                newKeyInput = ""
                                                newValueInput = ""
                                                isAddingNewPair = false
                                            }
                                        },
                                        enabled = newKeyInput.isNotBlank(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = rustOrange)
                                    ) {
                                        Text(stringResource(id = R.string.action_add), fontSize = 12.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    } else {
                        // Add Variable Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { isAddingNewPair = true }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = rustOrange,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(id = R.string.env_btn_add_variable),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                ),
                                color = rustOrange
                            )
                        }
                    }
                }
            } else {
                // Raw Editor Mode
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(id = R.string.env_raw_label),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = rawText,
                        onValueChange = { rawText = it },
                        placeholder = {
                            Text(
                                text = "API_KEY=sk-...\nDATABASE_URL=sqlite:///data.db\nPORT=8000",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            )
                        },
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        ),
                        minLines = 6,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = rustOrange,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Validation of variables
            val finalEnvMap: Map<String, String> = (if (editMode == EnvEditMode.RAW_TEXT) {
                rawText.lines()
                    .filter { it.contains("=") && !it.trim().startsWith("#") }
                    .associate {
                        val key = it.substringBefore("=").trim()
                        val value = it.substringAfter("=").trim()
                        key to value
                    }
            } else {
                envMap.toMap()
            }).filterKeys { it != "raw" && it.isNotBlank() }

            val emptyKeys = finalEnvMap.filter { it.value.isBlank() }.keys +
                    app.requiredEnvKeys.filter { it != "raw" && it.isNotBlank() }.filter { finalEnvMap[it].isNullOrBlank() }
            val isReadyToStart = emptyKeys.isEmpty()

            if (isValidationAttempted && !isReadyToStart) {
                Text(
                    text = stringResource(id = R.string.env_alert_all_required),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    ),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Action Buttons
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Primary: Save and Start
                Button(
                    onClick = {
                        isValidationAttempted = true
                        if (isReadyToStart) {
                            onSave(finalEnvMap, true)
                            onDismiss()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = rustOrange,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.env_btn_save_and_start),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    )
                }

                // Secondary: Save only
                OutlinedButton(
                    onClick = {
                        onSave(finalEnvMap, false)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.env_btn_save_only),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
