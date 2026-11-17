package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CallList
import com.example.data.model.ListStatus
import com.example.data.model.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.LoadingStateCard
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun CallListsScreen(
    callListsState: UiState<List<CallList>>,
    onOpenCreateList: () -> Unit,
    onOpenListDetail: (String) -> Unit,
    onDialList: (String) -> Unit,
    onDeleteList: (listId: String, name: String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf<ListStatus?>(null) }
    var pendingDeleteList by remember { mutableStateOf<CallList?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenCreateList,
                modifier = Modifier.testTag("create_call_list_fab"),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Create new call list")
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
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search calling lists...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = selectedStatus == null,
                        onClick = { selectedStatus = null },
                        label = { Text("All") }
                    )
                    FilterChip(
                        selected = selectedStatus == ListStatus.ACTIVE,
                        onClick = { selectedStatus = ListStatus.ACTIVE },
                        label = { Text("Active") }
                    )
                    FilterChip(
                        selected = selectedStatus == ListStatus.COMPLETED,
                        onClick = { selectedStatus = ListStatus.COMPLETED },
                        label = { Text("Completed") }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                when (callListsState) {
                    is UiState.Loading -> LoadingStateCard("Loading call lists...")
                    is UiState.Error -> ErrorStateCard(callListsState.message)
                    is UiState.Success -> {
                        val filtered = callListsState.data.filter { list ->
                            val matchesSearch = searchQuery.isBlank() ||
                                list.name.contains(searchQuery, ignoreCase = true) ||
                                list.description.contains(searchQuery, ignoreCase = true)
                            val matchesStatus = selectedStatus == null || list.listStatus == selectedStatus
                            matchesSearch && matchesStatus
                        }

                        if (filtered.isEmpty()) {
                            EmptyStateCard(
                                icon = Icons.Filled.FolderOpen,
                                title = if (callListsState.data.isEmpty()) "Create your first call list" else "No matching lists",
                                subtitle = "Import leads via CSV, bulk paste, or add individual contacts to launch an outbound power dialing campaign.",
                                action = {
                                    Button(
                                        onClick = onOpenCreateList,
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Create Call List")
                                    }
                                }
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 88.dp)
                            ) {
                                items(filtered, key = { it.id }) { callList ->
                                    CallListCard(
                                        callList = callList,
                                        onClick = { onOpenListDetail(callList.id) },
                                        onDialClick = { onDialList(callList.id) },
                                        onDeleteClick = { pendingDeleteList = callList }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDeleteList?.let { listToDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeleteList = null },
            title = { Text("Delete Call List?") },
            text = {
                Text("This will delete \"${listToDelete.name}\" and all ${listToDelete.totalLeads} leads inside it.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteList(listToDelete.id, listToDelete.name)
                        pendingDeleteList = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteList = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CallListCard(
    callList: CallList,
    onClick: () -> Unit,
    onDialClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val progress = if (callList.totalLeads > 0L) {
        (callList.dialedLeads.toFloat() / callList.totalLeads.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val remaining = (callList.totalLeads - callList.dialedLeads).coerceAtLeast(0L)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("call_list_card_${callList.id}"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = callList.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (callList.description.isNotBlank()) {
                        Text(
                            text = callList.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete list",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = EmeraldAccent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${callList.dialedLeads} / ${callList.totalLeads} dialed • $remaining pending",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(
                    onClick = onDialClick,
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Dial")
                }
            }
        }
    }
}
