package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CallList
import com.example.data.model.Lead
import com.example.data.model.MessageTemplate
import com.example.data.model.TemplateChannel
import com.example.ui.components.DispositionBadge
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.PhoneNumberText
import com.example.ui.components.QuickActionRow
import com.example.ui.theme.CobaltDark
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.CoralError
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.viewmodel.DialerMode
import com.example.ui.viewmodel.DialerState
import com.example.util.DialingAndCsvUtils

@Composable
fun DialerScreen(
    dialerState: DialerState,
    queueLeads: List<Lead>,
    callLists: List<CallList>,
    templates: List<MessageTemplate>,
    autoDialDelaySec: Int,
    onModeSelected: (DialerMode) -> Unit,
    onSelectList: (String?) -> Unit,
    onToggleAutoDial: (Boolean) -> Unit,
    onStartCountdown: () -> Unit,
    onCancelCountdown: () -> Unit,
    onSkipLead: (String) -> Unit,
    onCallLeadNow: (Lead) -> Unit,
    onEndCallAndLog: () -> Unit,
    onManualNumberChange: (String) -> Unit,
    onCallManualNumber: (String) -> Unit,
    onOpenListsTab: () -> Unit
) {
    val context = LocalContext.current
    var showScript by remember { mutableStateOf(false) }
    val scriptTemplate = remember(templates) {
        templates.firstOrNull { it.templateChannel == TemplateChannel.SCRIPT }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Mode Switcher
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    DialerMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = dialerState.mode == mode,
                            onClick = { onModeSelected(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = DialerMode.entries.size)
                        ) {
                            Text(
                                text = mode.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (dialerState.mode == DialerMode.KEYPAD) {
                item {
                    KeypadDialerCard(
                        number = dialerState.manualNumber,
                        isCallInProgress = dialerState.isCallInProgress,
                        elapsedSec = dialerState.callElapsedSec,
                        onNumberChange = onManualNumberChange,
                        onCallNow = { onCallManualNumber(dialerState.manualNumber) },
                        onEndAndLog = onEndCallAndLog
                    )
                }
            } else {
                // Campaign Filter Row (only for Queue mode)
                if (dialerState.mode == DialerMode.QUEUE && callLists.isNotEmpty()) {
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                FilterChip(
                                    selected = dialerState.selectedListId == null,
                                    onClick = { onSelectList(null) },
                                    label = { Text("All Pending") }
                                )
                            }
                            items(callLists, key = { it.id }) { list ->
                                FilterChip(
                                    selected = dialerState.selectedListId == list.id,
                                    onClick = { onSelectList(list.id) },
                                    label = { Text(list.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                )
                            }
                        }
                    }
                }

                // Auto-Dial Control Strip
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoMode,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Auto-Dial Queue",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "${queueLeads.size} in queue • ${autoDialDelaySec}s delay",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = dialerState.autoDialEnabled,
                                onCheckedChange = onToggleAutoDial
                            )
                        }
                    }
                }

                // Active Call Timer Banner
                if (dialerState.isCallInProgress && dialerState.activeCallLead != null) {
                    item {
                        ActiveCallBannerCard(
                            lead = dialerState.activeCallLead,
                            elapsedSec = dialerState.callElapsedSec,
                            onEndAndLog = onEndCallAndLog
                        )
                    }
                }

                val currentLead = queueLeads.firstOrNull()
                if (currentLead == null) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Filled.Dialpad,
                            title = if (dialerState.mode == DialerMode.CALLBACKS) "No scheduled callbacks" else "Queue is complete",
                            subtitle = if (dialerState.mode == DialerMode.CALLBACKS) {
                                "Callbacks scheduled from call dispositions will appear here."
                            } else {
                                "All pending leads in this campaign have been dialed. Select another list or import new leads."
                            },
                            action = {
                                FilledTonalButton(
                                    onClick = onOpenListsTab,
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text("Browse Call Lists")
                                }
                            }
                        )
                    }
                } else {
                    // Countdown Ring if active
                    if (dialerState.countdownSeconds != null) {
                        item {
                            CountdownBannerCard(
                                secondsLeft = dialerState.countdownSeconds,
                                totalSeconds = autoDialDelaySec.coerceAtLeast(1),
                                leadName = currentLead.fullName,
                                onPause = onCancelCountdown,
                                onCallNow = { onCallLeadNow(currentLead) }
                            )
                        }
                    }

                    // Current Lead Spotlight Card
                    item {
                        CurrentLeadCard(
                            lead = currentLead,
                            isCallInProgress = dialerState.isCallInProgress,
                            autoDialEnabled = dialerState.autoDialEnabled,
                            isCountdownRunning = dialerState.countdownSeconds != null,
                            onCallNow = { onCallLeadNow(currentLead) },
                            onStartAutoLoop = onStartCountdown,
                            onSkip = { onSkipLead(currentLead.id) },
                            onWhatsApp = {
                                DialingAndCsvUtils.launchWhatsApp(
                                    context,
                                    currentLead.phoneNumber,
                                    "Hi ${currentLead.fullName}, following up on your inquiry."
                                )
                            },
                            onSms = {
                                DialingAndCsvUtils.launchSms(
                                    context,
                                    currentLead.phoneNumber,
                                    "Hi ${currentLead.fullName}, tried reaching you on call."
                                )
                            }
                        )
                    }

                    // Expandable Call Script Helper
                    if (scriptTemplate != null) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showScript = !showScript },
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Filled.Description,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = scriptTemplate.title,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                        }
                                        Text(
                                            text = if (showScript) "Hide" else "Show Script",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    AnimatedVisibility(visible = showScript) {
                                        Text(
                                            text = DialingAndCsvUtils.personalizeTemplate(
                                                scriptTemplate.body,
                                                currentLead.fullName
                                            ),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(top = 10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Up Next Queue Preview
                    val upNext = queueLeads.drop(1).take(4)
                    if (upNext.isNotEmpty()) {
                        item {
                            Text(
                                text = "Up Next in Queue (${queueLeads.size - 1})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(upNext, key = { it.id }) { lead ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = lead.fullName,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        PhoneNumberText(lead.phoneNumber)
                                    }
                                    IconButton(
                                        onClick = { onCallLeadNow(lead) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Call,
                                            contentDescription = "Dial ${lead.fullName}",
                                            tint = EmeraldAccent
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ActiveCallBannerCard(
    lead: Lead,
    elapsedSec: Long,
    onEndAndLog: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(DeepNavy, CobaltDark)))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CALL IN PROGRESS",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF6EE7B7)
                        )
                        Text(
                            text = lead.fullName,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = lead.phoneNumber,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = EmeraldAccent.copy(alpha = 0.25f)
                    ) {
                        Text(
                            text = DialingAndCsvUtils.formatDuration(elapsedSec),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }

                Button(
                    onClick = onEndAndLog,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("end_call_log_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CoralError,
                        contentColor = Color.White
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Filled.CallEnd, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("End Call & Log Disposition")
                }
            }
        }
    }
}

@Composable
private fun CountdownBannerCard(
    secondsLeft: Int,
    totalSeconds: Int,
    leadName: String,
    onPause: () -> Unit,
    onCallNow: () -> Unit
) {
    val sweepFraction = (secondsLeft.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val ringColor = CobaltPrimary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * sweepFraction,
                        useCenter = false,
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = "${secondsLeft}s",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto-dialing next...",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = leadName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onPause, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Pause, contentDescription = "Pause countdown")
            }
            Button(
                onClick = onCallNow,
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent, contentColor = Color.White)
            ) {
                Text("Dial")
            }
        }
    }
}

@Composable
private fun CurrentLeadCard(
    lead: Lead,
    isCallInProgress: Boolean,
    autoDialEnabled: Boolean,
    isCountdownRunning: Boolean,
    onCallNow: () -> Unit,
    onStartAutoLoop: () -> Unit,
    onSkip: () -> Unit,
    onWhatsApp: () -> Unit,
    onSms: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "${lead.listName} • Attempt #${lead.callAttempts + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                DispositionBadge(disposition = lead.disposition)
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = lead.fullName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (lead.company.isNotBlank()) {
                    Text(
                        text = lead.company,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = lead.phoneNumber,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (lead.notes.isNotBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Notes: ${lead.notes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onCallNow,
                    enabled = !isCallInProgress,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("dialer_call_now_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldAccent,
                        contentColor = Color.White
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Filled.Call, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Call Now", style = MaterialTheme.typography.titleSmall)
                }

                if (autoDialEnabled && !isCountdownRunning && !isCallInProgress) {
                    FilledTonalButton(
                        onClick = onStartAutoLoop,
                        modifier = Modifier.height(54.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Start auto dial")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Auto")
                    }
                }

                OutlinedButton(
                    onClick = onSkip,
                    enabled = !isCallInProgress,
                    modifier = Modifier.height(54.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Skip lead")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quick Follow-Up",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                QuickActionRow(
                    onCall = onCallNow,
                    onWhatsApp = onWhatsApp,
                    onSms = onSms
                )
            }
        }
    }
}

@Composable
private fun KeypadDialerCard(
    number: String,
    isCallInProgress: Boolean,
    elapsedSec: Long,
    onNumberChange: (String) -> Unit,
    onCallNow: () -> Unit,
    onEndAndLog: () -> Unit
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("*", "0", "#")
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = number.ifBlank { "Enter phone number" },
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 26.sp
                    ),
                    color = if (number.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (number.isNotEmpty()) {
                    IconButton(
                        onClick = { onNumberChange(number.dropLast(1)) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Backspace"
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                keys.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.forEach { digit ->
                            Surface(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clickable { onNumberChange(number + digit) },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = digit,
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isCallInProgress) {
                Button(
                    onClick = onEndAndLog,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CoralError, contentColor = Color.White),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Filled.CallEnd, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("End & Log (${DialingAndCsvUtils.formatDuration(elapsedSec)})")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { onNumberChange("+") },
                        modifier = Modifier.height(54.dp)
                    ) {
                        Text("+")
                    }
                    Button(
                        onClick = onCallNow,
                        enabled = number.length >= 3,
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("keypad_call_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldAccent,
                            contentColor = Color.White
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Dial Number")
                    }
                }
            }
        }
    }
}
