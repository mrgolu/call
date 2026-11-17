package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.Lead
import com.example.data.model.LeadDisposition
import com.example.data.model.MessageTemplate
import com.example.data.model.TemplateChannel
import com.example.ui.components.dispositionColors
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.util.DialingAndCsvUtils
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DispositionSheet(
    lead: Lead,
    callDurationSec: Long,
    templates: List<MessageTemplate>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (
        disposition: LeadDisposition,
        notes: String,
        callbackAtMillis: Long,
        saveQuickDialName: String?
    ) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedDisposition by remember { mutableStateOf(LeadDisposition.INTERESTED) }
    var notes by remember { mutableStateOf(lead.notes) }
    var callbackAtMillis by remember { mutableLongStateOf(0L) }
    var quickDialSaveName by remember { mutableStateOf("") }

    val followUpTemplates = remember(templates) {
        templates.filter { it.templateChannel != TemplateChannel.SCRIPT }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Call Outcome",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "${lead.fullName} • ${lead.phoneNumber}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = DialingAndCsvUtils.formatDuration(callDurationSec),
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // Disposition grid chips
            Text(
                text = "Select Disposition",
                style = MaterialTheme.typography.titleSmall
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LeadDisposition.callableDispositions.forEach { disp ->
                    val (bg, fg) = dispositionColors(disp)
                    val selected = selectedDisposition == disp
                    FilterChip(
                        selected = selected,
                        onClick = {
                            selectedDisposition = disp
                            if (disp == LeadDisposition.CALLBACK && callbackAtMillis == 0L) {
                                callbackAtMillis = System.currentTimeMillis() + 3_600_000L
                            }
                        },
                        label = { Text(disp.label) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = bg,
                            selectedLabelColor = fg,
                            selectedLeadingIconColor = fg
                        )
                    )
                }
            }

            if (lead.id.isBlank()) {
                OutlinedTextField(
                    value = quickDialSaveName,
                    onValueChange = { quickDialSaveName = it },
                    label = { Text("Save Quick-Dial Contact Name (optional)") },
                    placeholder = { Text("Leave blank to skip saving") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Callback scheduler presets + custom picker
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (callbackAtMillis > 0L) {
                        "Callback Scheduled: ${DialingAndCsvUtils.formatDateTime(callbackAtMillis)}"
                    } else {
                        "Schedule Callback (optional)"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (callbackAtMillis > 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AssistChip(
                        onClick = { callbackAtMillis = System.currentTimeMillis() + 3_600_000L },
                        label = { Text("+1 Hour") }
                    )
                    AssistChip(
                        onClick = { callbackAtMillis = nextDayAt(hour = 10) },
                        label = { Text("Tomorrow 10 AM") }
                    )
                    AssistChip(
                        onClick = { callbackAtMillis = nextDayAt(hour = 15) },
                        label = { Text("Tomorrow 3 PM") }
                    )
                    AssistChip(
                        onClick = {
                            val cal = Calendar.getInstance()
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    TimePickerDialog(
                                        context,
                                        { _, hourOfDay, minute ->
                                            val chosen = Calendar.getInstance().apply {
                                                set(year, month, dayOfMonth, hourOfDay, minute, 0)
                                            }
                                            callbackAtMillis = chosen.timeInMillis
                                        },
                                        cal.get(Calendar.HOUR_OF_DAY),
                                        cal.get(Calendar.MINUTE),
                                        false
                                    ).show()
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        leadingIcon = { Icon(Icons.Filled.CalendarToday, contentDescription = null) },
                        label = { Text("Pick Date & Time") }
                    )
                    if (callbackAtMillis > 0L) {
                        AssistChip(
                            onClick = { callbackAtMillis = 0L },
                            label = { Text("Clear") }
                        )
                    }
                }
            }

            // Quick Follow-Up Message Templates
            if (followUpTemplates.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Instant Follow-Up",
                        style = MaterialTheme.typography.titleSmall
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        followUpTemplates.forEach { tpl ->
                            val message = DialingAndCsvUtils.personalizeTemplate(tpl.body, lead.fullName)
                            OutlinedButton(
                                onClick = {
                                    if (tpl.templateChannel == TemplateChannel.WHATSAPP) {
                                        DialingAndCsvUtils.launchWhatsApp(context, lead.phoneNumber, message)
                                    } else {
                                        DialingAndCsvUtils.launchSms(context, lead.phoneNumber, message)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (tpl.templateChannel == TemplateChannel.WHATSAPP) Icons.Filled.Chat else Icons.Filled.Sms,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(tpl.title)
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Call Notes") },
                placeholder = { Text("Key takeaways, budget, objections...") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isSaving,
                    modifier = Modifier
                        .weight(0.4f)
                        .height(52.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        onSubmit(
                            selectedDisposition,
                            notes.trim(),
                            callbackAtMillis,
                            quickDialSaveName.trim().ifBlank { null }
                        )
                    },
                    enabled = !isSaving,
                    modifier = Modifier
                        .weight(0.6f)
                        .height(52.dp)
                        .testTag("save_disposition_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CobaltPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Text(if (isSaving) "Saving..." else "Save & Continue")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

private fun nextDayAt(hour: Int): Long {
    return Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
