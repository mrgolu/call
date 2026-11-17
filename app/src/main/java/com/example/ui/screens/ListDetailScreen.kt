package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CallList
import com.example.data.model.Lead
import com.example.data.model.LeadDisposition
import com.example.ui.components.DispositionBadge
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.PhoneNumberText
import com.example.ui.components.QuickActionRow
import com.example.ui.theme.EmeraldAccent
import com.example.util.DialingAndCsvUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreen(
    callList: CallList,
    leads: List<Lead>,
    onBack: () -> Unit,
    onAddLeadsToList: () -> Unit,
    onStartAutoDialer: () -> Unit,
    onCallLead: (Lead) -> Unit,
    onEditLead: (leadId: String, name: String, phone: String, company: String, email: String, notes: String) -> Unit,
    onDeleteLead: (Lead) -> Unit
) {
    val context = LocalContext.current
    var statusFilter by remember { mutableStateOf<LeadDisposition?>(null) }
    var editingLead by remember { mutableStateOf<Lead?>(null) }

    BackHandler { onBack() }

    val filteredLeads = remember(leads, statusFilter) {
        if (statusFilter == null) leads
        else leads.filter { it.disposition == statusFilter }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = callList.name,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${leads.size} leads • ${leads.count { it.disposition == LeadDisposition.PENDING }} pending",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to lists"
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = onStartAutoDialer,
                        enabled = leads.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldAccent,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Auto Dial")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddLeadsToList,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add leads to list")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 820.dp)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = statusFilter == null,
                            onClick = { statusFilter = null },
                            label = { Text("All (${leads.size})") }
                        )
                    }
                    items(LeadDisposition.entries) { disp ->
                        val count = leads.count { it.disposition == disp }
                        if (count > 0 || disp == LeadDisposition.PENDING) {
                            FilterChip(
                                selected = statusFilter == disp,
                                onClick = { statusFilter = disp },
                                label = { Text("${disp.label} ($count)") }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (filteredLeads.isEmpty()) {
                    EmptyStateCard(
                        icon = Icons.Filled.PersonOutline,
                        title = "No leads in this view",
                        subtitle = "Add contacts individually, paste numbers, or upload a CSV sheet to start calling.",
                        action = {
                            Button(onClick = onAddLeadsToList, modifier = Modifier.height(48.dp)) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Leads")
                            }
                        }
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 88.dp)
                    ) {
                        items(filteredLeads, key = { it.id }) { lead ->
                            LeadItemCard(
                                lead = lead,
                                onCall = { onCallLead(lead) },
                                onWhatsApp = {
                                    DialingAndCsvUtils.launchWhatsApp(
                                        context,
                                        lead.phoneNumber,
                                        "Hi ${lead.fullName}, following up regarding our call."
                                    )
                                },
                                onSms = {
                                    DialingAndCsvUtils.launchSms(
                                        context,
                                        lead.phoneNumber,
                                        "Hi ${lead.fullName}, following up regarding our call."
                                    )
                                },
                                onEdit = { editingLead = lead },
                                onDelete = { onDeleteLead(lead) }
                            )
                        }
                    }
                }
            }
        }
    }

    editingLead?.let { lead ->
        EditLeadDialog(
            lead = lead,
            onDismiss = { editingLead = null },
            onSave = { name, phone, company, email, notes ->
                onEditLead(lead.id, name, phone, company, email, notes)
                editingLead = null
            }
        )
    }
}

@Composable
private fun LeadItemCard(
    lead: Lead,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit,
    onSms: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
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
                    if (lead.company.isNotBlank()) {
                        Text(
                            text = lead.company,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                DispositionBadge(disposition = lead.disposition)
            }

            PhoneNumberText(lead.phoneNumber)

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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickActionRow(
                    onCall = onCall,
                    onWhatsApp = onWhatsApp,
                    onSms = onSms
                )
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit lead")
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "Delete lead",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditLeadDialog(
    lead: Lead,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, company: String, email: String, notes: String) -> Unit
) {
    var name by remember { mutableStateOf(lead.fullName) }
    var phone by remember { mutableStateOf(lead.phoneNumber) }
    var company by remember { mutableStateOf(lead.company) }
    var email by remember { mutableStateOf(lead.email) }
    var notes by remember { mutableStateOf(lead.notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Lead") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, singleLine = true)
                OutlinedTextField(value = company, onValueChange = { company = it }, label = { Text("Company") }, singleLine = true)
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, maxLines = 3)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, phone, company, email, notes) },
                enabled = name.isNotBlank() && phone.length >= 3
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
