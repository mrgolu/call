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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CallLog
import com.example.data.model.Lead
import com.example.data.model.LeadDisposition
import com.example.data.model.UiState
import com.example.ui.components.DispositionBadge
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.LoadingStateCard
import com.example.ui.components.PhoneNumberText
import com.example.ui.components.QuickActionRow
import com.example.ui.theme.CoralError
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.util.DialingAndCsvUtils

@Composable
fun ActivityScreen(
    leadsState: UiState<List<Lead>>,
    callLogsState: UiState<List<CallLog>>,
    initialShowHistory: Boolean = false,
    onCallLead: (Lead) -> Unit,
    onCompleteCallback: (String) -> Unit,
    onRescheduleCallback: (leadId: String, newMillis: Long) -> Unit,
    onRedialPhone: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedSegment by remember(initialShowHistory) { mutableIntStateOf(if (initialShowHistory) 1 else 0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedDispFilter by remember { mutableStateOf<LeadDisposition?>(null) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedSegment == 0,
                    onClick = { selectedSegment = 0 },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) {
                    Text("Callbacks")
                }
                SegmentedButton(
                    selected = selectedSegment == 1,
                    onClick = { selectedSegment = 1 },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) {
                    Text("Call History")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (selectedSegment == 0) {
                when (leadsState) {
                    is UiState.Loading -> LoadingStateCard("Loading scheduled callbacks...")
                    is UiState.Error -> ErrorStateCard(leadsState.message)
                    is UiState.Success -> {
                        val callbacks = leadsState.data
                            .filter { it.callbackAtMillis > 0L && !it.callbackCompleted }
                            .sortedBy { it.callbackAtMillis }

                        if (callbacks.isEmpty()) {
                            EmptyStateCard(
                                icon = Icons.Filled.EventRepeat,
                                title = "No pending callbacks",
                                subtitle = "Schedule follow-up callbacks right from the post-call disposition sheet."
                            )
                        } else {
                            val now = System.currentTimeMillis()
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 24.dp)
                            ) {
                                items(callbacks, key = { it.id }) { lead ->
                                    val isOverdue = lead.callbackAtMillis < now
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.medium,
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isOverdue) CoralError.copy(alpha = 0.5f)
                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = lead.fullName,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    PhoneNumberText(lead.phoneNumber)
                                                }
                                                DispositionBadge(disposition = lead.disposition)
                                            }
                                            Text(
                                                text = (if (isOverdue) "Overdue: " else "Scheduled: ") +
                                                    DialingAndCsvUtils.formatDateTime(lead.callbackAtMillis),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (isOverdue) CoralError else MaterialTheme.colorScheme.primary
                                            )
                                            if (lead.notes.isNotBlank()) {
                                                Text(
                                                    text = lead.notes,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                FilledTonalButton(
                                                    onClick = { onCallLead(lead) },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(48.dp)
                                                ) {
                                                    Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Call Now")
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        onRescheduleCallback(lead.id, System.currentTimeMillis() + 3_600_000L)
                                                    },
                                                    modifier = Modifier.height(48.dp)
                                                ) {
                                                    Icon(Icons.Filled.Schedule, contentDescription = "Snooze 1 hour", modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("+1h")
                                                }
                                                IconButton(
                                                    onClick = { onCompleteCallback(lead.id) },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.CheckCircleOutline,
                                                        contentDescription = "Mark callback done",
                                                        tint = MaterialTheme.colorScheme.secondary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search name, phone, or notes...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedDispFilter == null,
                            onClick = { selectedDispFilter = null },
                            label = { Text("All") }
                        )
                    }
                    items(LeadDisposition.callableDispositions) { disp ->
                        FilterChip(
                            selected = selectedDispFilter == disp,
                            onClick = { selectedDispFilter = disp },
                            label = { Text(disp.label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                when (callLogsState) {
                    is UiState.Loading -> LoadingStateCard("Loading call history...")
                    is UiState.Error -> ErrorStateCard(callLogsState.message)
                    is UiState.Success -> {
                        val filteredLogs = callLogsState.data.filter { log ->
                            val matchesDisp = selectedDispFilter == null || log.leadDisposition == selectedDispFilter
                            val matchesSearch = searchQuery.isBlank() ||
                                log.leadName.contains(searchQuery, ignoreCase = true) ||
                                log.phoneNumber.contains(searchQuery, ignoreCase = true) ||
                                log.notes.contains(searchQuery, ignoreCase = true)
                            matchesDisp && matchesSearch
                        }

                        if (filteredLogs.isEmpty()) {
                            EmptyStateCard(
                                icon = Icons.Filled.History,
                                title = "No call logs yet",
                                subtitle = "Every call you complete through the Power Dialer or Keypad is logged here automatically."
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 24.dp)
                            ) {
                                items(filteredLogs, key = { it.id }) { log ->
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
                                                    Text(
                                                        text = log.leadName,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    PhoneNumberText(log.phoneNumber)
                                                }
                                                DispositionBadge(disposition = log.leadDisposition)
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = DialingAndCsvUtils.formatDateTime(
                                                        log.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Surface(
                                                    shape = MaterialTheme.shapes.small,
                                                    color = MaterialTheme.colorScheme.surfaceVariant
                                                ) {
                                                    Text(
                                                        text = DialingAndCsvUtils.formatDuration(log.durationSec),
                                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                            if (log.notes.isNotBlank()) {
                                                Text(
                                                    text = log.notes,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            QuickActionRow(
                                                onCall = { onRedialPhone(log.phoneNumber) },
                                                onWhatsApp = {
                                                    DialingAndCsvUtils.launchWhatsApp(
                                                        context,
                                                        log.phoneNumber,
                                                        "Hi ${log.leadName}, following up on our recent call."
                                                    )
                                                },
                                                onSms = {
                                                    DialingAndCsvUtils.launchSms(
                                                        context,
                                                        log.phoneNumber,
                                                        "Hi ${log.leadName}, following up on our recent call."
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
