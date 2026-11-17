package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.CallList
import com.example.data.model.ParsedCsvLead
import com.example.util.DialingAndCsvUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeadImportSheet(
    targetExistingList: CallList?,
    defaultCountryCode: String,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onCreateListWithLeads: (name: String, description: String, leads: List<ParsedCsvLead>) -> Unit,
    onAddLeadsToList: (list: CallList, leads: List<ParsedCsvLead>) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var listName by remember { mutableStateOf("") }
    var listDescription by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Single Lead, 1 = Bulk/CSV Paste, 2 = Upload CSV File

    // Single lead fields
    var singleName by remember { mutableStateOf("") }
    var singlePhone by remember { mutableStateOf("") }
    var singleCompany by remember { mutableStateOf("") }
    var singleEmail by remember { mutableStateOf("") }
    var singleNotes by remember { mutableStateOf("") }

    // Bulk/CSV text & parsed leads
    var bulkRawText by remember { mutableStateOf("") }
    var csvFileName by remember { mutableStateOf<String?>(null) }
    var csvFileLeads by remember { mutableStateOf<List<ParsedCsvLead>>(emptyList()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val csvFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!content.isNullOrBlank()) {
                    val parsed = DialingAndCsvUtils.parseCsvInput(content, defaultCountryCode)
                    csvFileLeads = parsed
                    csvFileName = uri.lastPathSegment ?: "selected.csv"
                    errorMsg = if (parsed.isEmpty()) "No valid phone numbers found in the file." else null
                } else {
                    errorMsg = "Selected file is empty."
                }
            } catch (e: Exception) {
                errorMsg = e.localizedMessage ?: "Failed to read file."
            }
        }
    }

    val bulkParsed = remember(bulkRawText, defaultCountryCode) {
        DialingAndCsvUtils.parseCsvInput(bulkRawText, defaultCountryCode)
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (targetExistingList == null) "Create Call List & Import Leads" else "Add Leads to ${targetExistingList.name}",
                style = MaterialTheme.typography.headlineSmall
            )

            if (targetExistingList == null) {
                OutlinedTextField(
                    value = listName,
                    onValueChange = { listName = it; errorMsg = null },
                    label = { Text("Campaign / List Name *") },
                    placeholder = { Text("e.g., November Demo Requests") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_list_name_input")
                )
                OutlinedTextField(
                    value = listDescription,
                    onValueChange = { listDescription = it },
                    label = { Text("Description (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Input mode selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0; errorMsg = null },
                    label = { Text("Single") },
                    leadingIcon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1; errorMsg = null },
                    label = { Text("Paste") },
                    leadingIcon = { Icon(Icons.Filled.ContentPaste, contentDescription = null) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2; errorMsg = null },
                    label = { Text("CSV") },
                    leadingIcon = { Icon(Icons.Filled.UploadFile, contentDescription = null) },
                    modifier = Modifier.weight(1f)
                )
            }

            when (selectedTab) {
                0 -> {
                    OutlinedTextField(
                        value = singleName,
                        onValueChange = { singleName = it; errorMsg = null },
                        label = { Text("Lead Full Name *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("single_lead_name_input")
                    )
                    OutlinedTextField(
                        value = singlePhone,
                        onValueChange = { singlePhone = it; errorMsg = null },
                        label = { Text("Phone Number *") },
                        placeholder = { Text("+919876543210") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("single_lead_phone_input")
                    )
                    OutlinedTextField(
                        value = singleCompany,
                        onValueChange = { singleCompany = it },
                        label = { Text("Company (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = singleEmail,
                        onValueChange = { singleEmail = it },
                        label = { Text("Email (optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = singleNotes,
                        onValueChange = { singleNotes = it },
                        label = { Text("Initial Notes (optional)") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                1 -> {
                    Text(
                        text = "Paste one lead per line: Name, Phone, Company, Email — or paste plain phone numbers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = bulkRawText,
                        onValueChange = { bulkRawText = it; errorMsg = null },
                        label = { Text("Leads / Numbers") },
                        placeholder = { Text("Rahul Verma, 9876543210, Acme Corp\nAnanya Roy, 9820011223") },
                        minLines = 5,
                        maxLines = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bulk_paste_input")
                    )
                    Text(
                        text = "${bulkParsed.size} valid contacts detected",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                2 -> {
                    Text(
                        text = "Pick a .csv or .txt file (zero storage permission required). Columns: Name, Phone, Company, Email, Notes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            csvFileLauncher.launch(
                                arrayOf(
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "text/plain",
                                    "application/csv"
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(csvFileName ?: "Choose CSV / Text File")
                    }
                    if (csvFileLeads.isNotEmpty()) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Ready to import ${csvFileLeads.size} leads from file.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            if (errorMsg != null) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMsg.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Button(
                onClick = {
                    if (targetExistingList == null && listName.isBlank()) {
                        errorMsg = "Please enter a campaign / list name."
                        return@Button
                    }
                    val leadsToSave: List<ParsedCsvLead> = when (selectedTab) {
                        0 -> {
                            val sanitized = DialingAndCsvUtils.sanitizePhoneNumber(singlePhone, defaultCountryCode)
                            if (singleName.isBlank() || sanitized.length < 6) {
                                errorMsg = "Enter a valid lead name and phone number."
                                return@Button
                            }
                            listOf(
                                ParsedCsvLead(
                                    fullName = singleName.trim(),
                                    phoneNumber = sanitized,
                                    company = singleCompany.trim(),
                                    email = singleEmail.trim(),
                                    notes = singleNotes.trim()
                                )
                            )
                        }
                        1 -> bulkParsed
                        else -> csvFileLeads
                    }

                    if (targetExistingList != null && leadsToSave.isEmpty()) {
                        errorMsg = "Please provide at least one valid lead to add."
                        return@Button
                    }

                    if (targetExistingList == null) {
                        onCreateListWithLeads(listName.trim(), listDescription.trim(), leadsToSave)
                    } else {
                        onAddLeadsToList(targetExistingList, leadsToSave)
                    }
                },
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_import_leads_button")
            ) {
                Text(
                    text = when {
                        isSaving -> "Saving..."
                        targetExistingList == null -> "Create List"
                        else -> "Add Leads"
                    }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
