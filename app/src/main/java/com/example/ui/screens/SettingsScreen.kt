package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AgentProfile
import com.example.data.model.MessageTemplate
import com.example.data.model.TemplateChannel
import com.example.data.model.UiState
import com.example.ui.components.LoadingStateCard
import com.example.ui.theme.JetBrainsMonoFamily
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    agentProfile: AgentProfile?,
    templatesState: UiState<List<MessageTemplate>>,
    isMutating: Boolean,
    onSaveSettings: (
        displayName: String,
        autoDialDelaySec: Long,
        dailyCallTarget: Long,
        defaultCountryCode: String,
        autoAdvanceDialer: Boolean
    ) -> Unit,
    onSaveTemplate: (templateId: String?, title: String, channel: TemplateChannel, body: String) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onSignOut: () -> Unit
) {
    var displayName by remember { mutableStateOf(agentProfile?.displayName ?: "") }
    var countryCode by remember { mutableStateOf(agentProfile?.defaultCountryCode ?: "+91") }
    var autoAdvance by remember { mutableStateOf(agentProfile?.autoAdvanceDialer ?: true) }
    var delaySlider by remember { mutableFloatStateOf((agentProfile?.autoDialDelaySec ?: 5L).toFloat()) }
    var targetSlider by remember { mutableFloatStateOf((agentProfile?.dailyCallTarget ?: 50L).toFloat()) }

    LaunchedEffect(agentProfile) {
        if (agentProfile != null) {
            displayName = agentProfile.displayName
            countryCode = agentProfile.defaultCountryCode
            autoAdvance = agentProfile.autoAdvanceDialer
            delaySlider = agentProfile.autoDialDelaySec.toFloat()
            targetSlider = agentProfile.dailyCallTarget.toFloat()
        }
    }

    var showTemplateDialog by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<MessageTemplate?>(null) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Agent Profile & Sign Out
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = agentProfile?.displayName?.ifBlank { "Agent" } ?: "Agent",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = agentProfile?.email?.ifBlank { "Google Workspace" } ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = onSignOut,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sign Out")
                        }
                    }
                }
            }

            // 2. Dialer Preferences
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Dialer Preferences",
                            style = MaterialTheme.typography.titleLarge
                        )
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("Agent Display Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = countryCode,
                            onValueChange = { countryCode = it.take(6) },
                            label = { Text("Default Country Code") },
                            placeholder = { Text("+91") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Advance Queue", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Start next countdown automatically after saving call outcome",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = autoAdvance, onCheckedChange = { autoAdvance = it })
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Auto-Dial Delay", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${delaySlider.roundToInt()}s",
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = delaySlider,
                                onValueChange = { delaySlider = it },
                                valueRange = 2f..15f,
                                steps = 12
                            )
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Daily Call Target", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${targetSlider.roundToInt()} calls",
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = targetSlider,
                                onValueChange = { targetSlider = it },
                                valueRange = 10f..200f,
                                steps = 18
                            )
                        }

                        Button(
                            onClick = {
                                onSaveSettings(
                                    displayName.trim().ifBlank { "Sales Agent" },
                                    delaySlider.roundToInt().toLong(),
                                    targetSlider.roundToInt().toLong(),
                                    countryCode.trim().ifBlank { "+91" },
                                    autoAdvance
                                )
                            },
                            enabled = !isMutating,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Dialer Preferences")
                        }
                    }
                }
            }

            // 3. Call Scripts & WhatsApp/SMS Templates
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Scripts & Follow-Up Templates",
                        style = MaterialTheme.typography.titleLarge
                    )
                    FilledAddTemplateButton {
                        editingTemplate = null
                        showTemplateDialog = true
                    }
                }
            }

            when (templatesState) {
                is UiState.Loading -> item { LoadingStateCard("Loading templates...") }
                is UiState.Error -> item {
                    Text(
                        text = templatesState.message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is UiState.Success -> {
                    items(templatesState.data, key = { it.id }) { tpl ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(tpl.title, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            tpl.templateChannel.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Row {
                                        IconButton(
                                            onClick = {
                                                editingTemplate = tpl
                                                showTemplateDialog = true
                                            },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Edit template")
                                        }
                                        IconButton(
                                            onClick = { onDeleteTemplate(tpl.id) },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.DeleteOutline,
                                                contentDescription = "Delete template",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = tpl.body,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showTemplateDialog) {
        TemplateEditorDialog(
            initial = editingTemplate,
            onDismiss = { showTemplateDialog = false },
            onSave = { title, channel, body ->
                onSaveTemplate(editingTemplate?.id, title, channel, body)
                showTemplateDialog = false
            }
        )
    }
}

@Composable
private fun FilledAddTemplateButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.height(48.dp)) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(4.dp))
        Text("Add")
    }
}

@Composable
private fun TemplateEditorDialog(
    initial: MessageTemplate?,
    onDismiss: () -> Unit,
    onSave: (title: String, channel: TemplateChannel, body: String) -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var channel by remember { mutableStateOf(initial?.templateChannel ?: TemplateChannel.WHATSAPP) }
    var body by remember { mutableStateOf(initial?.body ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Template" else "Edit Template") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TemplateChannel.entries.forEach { ch ->
                        FilterChip(
                            selected = channel == ch,
                            onClick = { channel = ch },
                            label = { Text(ch.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Message / Script ({name} supported)") },
                    minLines = 3,
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, channel, body) },
                enabled = title.isNotBlank() && body.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
