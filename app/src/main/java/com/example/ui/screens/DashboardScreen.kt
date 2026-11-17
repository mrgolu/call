package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DialerSip
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AgentProfile
import com.example.data.model.CallList
import com.example.data.model.Lead
import com.example.data.model.LeadDisposition
import com.example.data.model.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.LoadingStateCard
import com.example.ui.components.PhoneNumberText
import com.example.ui.components.dispositionColors
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.CobaltDark
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.CoralError
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.viewmodel.DashboardMetrics
import com.example.util.DialingAndCsvUtils

@Composable
fun DashboardScreen(
    agentProfile: AgentProfile?,
    metrics: DashboardMetrics,
    callListsState: UiState<List<CallList>>,
    leadsState: UiState<List<Lead>>,
    isMutating: Boolean,
    onStartPowerDialer: () -> Unit,
    onOpenCreateList: () -> Unit,
    onLoadStarterCampaign: () -> Unit,
    onOpenListDetail: (String) -> Unit,
    onDialLead: (Lead) -> Unit,
    onOpenCallbacksTab: () -> Unit
) {
    val lists = (callListsState as? UiState.Success)?.data.orEmpty()
    val leads = (leadsState as? UiState.Success)?.data.orEmpty()
    val now = System.currentTimeMillis()
    val upcomingCallbacks = leads
        .filter { it.callbackAtMillis > 0L && !it.callbackCompleted }
        .sortedBy { it.callbackAtMillis }
        .take(4)

    val dailyTarget = (agentProfile?.dailyCallTarget ?: 50L).toInt().coerceAtLeast(1)
    val targetFraction = (metrics.callsToday.toFloat() / dailyTarget.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(targetValue = targetFraction, label = "target_progress")

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Hero Daily Target & Power Dialer CTA
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(DeepNavy, CobaltDark, CobaltPrimary)
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Welcome back,",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.78f)
                                    )
                                    Text(
                                        text = agentProfile?.displayName?.ifBlank { "Agent" } ?: "Agent",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = "${metrics.callsToday} / $dailyTarget calls",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = JetBrainsMonoFamily
                                        ),
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(CircleShape),
                                    color = EmeraldAccent,
                                    trackColor = Color.White.copy(alpha = 0.22f),
                                    strokeCap = StrokeCap.Round
                                )
                                Text(
                                    text = if (metrics.pendingQueueCount > 0) {
                                        "${metrics.pendingQueueCount} pending leads ready in your calling queue"
                                    } else {
                                        "Import a lead list or load a starter campaign to begin dialing"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = onStartPowerDialer,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("dashboard_start_dialer_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldAccent,
                                        contentColor = Color.White
                                    ),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Power Dialer",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                                OutlinedButton(
                                    onClick = onOpenCreateList,
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("dashboard_import_list_button"),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color.White
                                    ),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("New List")
                                }
                            }
                        }
                    }
                }
            }

            // 2. 2x2 KPI Stat Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        KpiMetricCard(
                            title = "Calls Today",
                            value = metrics.callsToday.toString(),
                            subtitle = "${metrics.connectedToday} connected",
                            icon = Icons.Filled.Call,
                            accentColor = CobaltPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMetricCard(
                            title = "Connect Rate",
                            value = "${metrics.connectedRatePercent}%",
                            subtitle = "Of calls logged today",
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            accentColor = EmeraldAccent,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        KpiMetricCard(
                            title = "Interested",
                            value = metrics.interestedCount.toString(),
                            subtitle = "Hot pipeline leads",
                            icon = Icons.Filled.CheckCircle,
                            accentColor = EmeraldAccent,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMetricCard(
                            title = "Pending Queue",
                            value = metrics.pendingQueueCount.toString(),
                            subtitle = "${metrics.dueCallbacksCount} callbacks due",
                            icon = Icons.Filled.HourglassTop,
                            accentColor = AmberWarm,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 3. Loading / Error / Empty CTA for campaigns
            when (callListsState) {
                is UiState.Loading -> {
                    item { LoadingStateCard("Loading your calling campaigns...") }
                }
                is UiState.Error -> {
                    item { ErrorStateCard(callListsState.message) }
                }
                is UiState.Success -> {
                    if (lists.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Filled.DialerSip,
                                title = "No calling lists yet",
                                subtitle = "Create your own CSV/bulk calling list or load 5 realistic sample leads to test the auto-dialer right away.",
                                action = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        FilledTonalButton(
                                            onClick = onLoadStarterCampaign,
                                            enabled = !isMutating,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("load_starter_campaign_button")
                                        ) {
                                            Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Load Starter List")
                                        }
                                        Button(
                                            onClick = onOpenCreateList,
                                            enabled = !isMutating,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                        ) {
                                            Icon(Icons.Filled.UploadFile, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Import CSV")
                                        }
                                    }
                                }
                            )
                        }
                    } else {
                        // Active Lists Preview
                        item {
                            SectionHeaderRow(
                                title = "Active Campaigns",
                                actionLabel = "${lists.size} lists",
                                onActionClick = null
                            )
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                lists.take(3).forEach { callList ->
                                    val progress = if (callList.totalLeads > 0L) {
                                        (callList.dialedLeads.toFloat() / callList.totalLeads.toFloat()).coerceIn(0f, 1f)
                                    } else 0f
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenListDetail(callList.id) },
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
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.FolderOpen,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = callList.name,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Text(
                                                    text = "${callList.dialedLeads}/${callList.totalLeads}",
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        fontFamily = JetBrainsMonoFamily
                                                    ),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            LinearProgressIndicator(
                                                progress = { progress },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(6.dp)
                                                    .clip(CircleShape),
                                                color = EmeraldAccent,
                                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                                strokeCap = StrokeCap.Round
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Upcoming / Due Callbacks Strip
            if (upcomingCallbacks.isNotEmpty()) {
                item {
                    SectionHeaderRow(
                        title = "Scheduled Callbacks",
                        actionLabel = "View All",
                        onActionClick = onOpenCallbacksTab
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        upcomingCallbacks.forEach { lead ->
                            val isOverdue = lead.callbackAtMillis < now
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(
                                    1.dp,
                                    if (isOverdue) CoralError.copy(alpha = 0.45f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(40.dp),
                                        shape = CircleShape,
                                        color = if (isOverdue) CoralError.copy(alpha = 0.14f)
                                        else MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.EventRepeat,
                                                contentDescription = null,
                                                tint = if (isOverdue) CoralError else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = lead.fullName,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        PhoneNumberText(lead.phoneNumber)
                                        Text(
                                            text = (if (isOverdue) "Overdue • " else "Due • ") +
                                                DialingAndCsvUtils.formatDateTime(lead.callbackAtMillis),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isOverdue) CoralError else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = { onDialLead(lead) },
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Call")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Disposition Analytics Breakdown
            if (metrics.dispositionBreakdown.isNotEmpty()) {
                item {
                    SectionHeaderRow(title = "Lead Disposition Breakdown")
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val totalDialed = metrics.dispositionBreakdown.sumOf { it.second }.coerceAtLeast(1)
                            metrics.dispositionBreakdown.forEach { (disp, count) ->
                                val (_, fgColor) = dispositionColors(disp)
                                val fraction = (count.toFloat() / totalDialed.toFloat()).coerceIn(0.03f, 1f)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = disp.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "$count (${(count * 100 / totalDialed)}%)",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontFamily = JetBrainsMonoFamily
                                            ),
                                            color = fgColor
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { fraction },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(7.dp)
                                            .clip(CircleShape),
                                        color = fgColor,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                        strokeCap = StrokeCap.Round
                                    )
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
private fun KpiMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
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
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    modifier = Modifier.size(30.dp),
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.14f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (actionLabel != null) {
            if (onActionClick != null) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onActionClick() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            } else {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
