package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DialerSip
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DialerSip
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.auth.signOut
import com.example.data.model.CallList
import com.example.data.model.UiState
import com.example.data.repository.CallFlowRepository
import com.example.ui.viewmodel.CallFlowViewModel
import com.example.util.DialingAndCsvUtils
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_LISTS = "lists"
const val ROUTE_DIALER = "dialer"
const val ROUTE_ACTIVITY = "activity"
const val ROUTE_SETTINGS = "settings"
const val ROUTE_LIST_DETAIL = "list_detail/{listId}"

private data class BottomNavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val NAV_TABS = listOf(
    BottomNavTab(ROUTE_DASHBOARD, "Home", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    BottomNavTab(ROUTE_LISTS, "Lists", Icons.Filled.FolderCopy, Icons.Outlined.FolderCopy),
    BottomNavTab(ROUTE_DIALER, "Dialer", Icons.Filled.DialerSip, Icons.Outlined.DialerSip),
    BottomNavTab(ROUTE_ACTIVITY, "Activity", Icons.Filled.History, Icons.Outlined.History),
    BottomNavTab(ROUTE_SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    currentUserId: String,
    userDisplayName: String,
    userEmail: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: CallFlowViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                CallFlowViewModel(
                    repository = CallFlowRepository(db),
                    currentUserId = currentUserId,
                    initialDisplayName = userDisplayName,
                    initialEmail = userEmail
                )
            }
        }
    )

    val agentProfile by viewModel.agentProfile.collectAsStateWithLifecycle()
    val callListsState by viewModel.callListsState.collectAsStateWithLifecycle()
    val leadsState by viewModel.leadsState.collectAsStateWithLifecycle()
    val callLogsState by viewModel.callLogsState.collectAsStateWithLifecycle()
    val templatesState by viewModel.templatesState.collectAsStateWithLifecycle()
    val metrics by viewModel.dashboardMetrics.collectAsStateWithLifecycle()
    val dialerState by viewModel.dialerState.collectAsStateWithLifecycle()
    val isMutating by viewModel.isMutating.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Lead import modal state (null = closed; CallList? = add to existing list; createNew = true)
    var showImportSheet by remember { mutableStateOf(false) }
    var importTargetList by remember { mutableStateOf<CallList?>(null) }
    var activityDefaultHistory by remember { mutableStateOf(false) }

    // Pending phone number waiting for CALL_PHONE permission prompt
    var pendingCallNumber by remember { mutableStateOf<String?>(null) }
    var wasPausedAfterDial by remember { mutableStateOf(false) }
    var dialLaunchedExternally by remember { mutableStateOf(false) }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val phone = pendingCallNumber
        pendingCallNumber = null
        if (!phone.isNullOrBlank()) {
            dialLaunchedExternally = DialingAndCsvUtils.launchPhoneCall(
                context = context,
                phoneNumber = phone,
                hasCallPermission = granted
            )
            wasPausedAfterDial = false
        }
    }

    // Collect toast / snackbar messages
    LaunchedEffect(viewModel) {
        viewModel.toastMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Collect dial intents from ViewModel (both manual & auto-dial countdown)
    LaunchedEffect(viewModel) {
        viewModel.dialLaunchRequest.collect { phone ->
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                dialLaunchedExternally = DialingAndCsvUtils.launchPhoneCall(
                    context = context,
                    phoneNumber = phone,
                    hasCallPermission = true
                )
                wasPausedAfterDial = false
            } else {
                pendingCallNumber = phone
                callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            }
        }
    }

    // Observe Activity lifecycle so returning from the system Phone app automatically opens Disposition Sheet!
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (dialLaunchedExternally && viewModel.dialerState.value.isCallInProgress) {
                        wasPausedAfterDial = true
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (wasPausedAfterDial && viewModel.dialerState.value.isCallInProgress) {
                        wasPausedAfterDial = false
                        dialLaunchedExternally = false
                        viewModel.openDispositionForCurrentCall()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val allLists = (callListsState as? UiState.Success)?.data.orEmpty()
    val allLeads = (leadsState as? UiState.Success)?.data.orEmpty()
    val allTemplates = (templatesState as? UiState.Success)?.data.orEmpty()
    val queueLeads = remember(allLeads, dialerState.mode, dialerState.selectedListId, dialerState.skippedLeadIds) {
        viewModel.getCurrentQueueLeads()
    }

    val isDetailRoute = currentRoute?.startsWith("list_detail/") == true

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                if (!isDetailRoute) {
                    val title = when (currentRoute) {
                        ROUTE_LISTS -> "Calling Lists"
                        ROUTE_DIALER -> "Power Dialer"
                        ROUTE_ACTIVITY -> "Callbacks & Activity"
                        ROUTE_SETTINGS -> "Agent Settings"
                        else -> "CallFlow"
                    }
                    TopAppBar(
                        title = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }
            },
            bottomBar = {
                if (!useRail && !isDetailRoute) {
                    NavigationBar {
                        NAV_TABS.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                modifier = Modifier.testTag("nav_tab_${tab.route}"),
                                icon = {
                                    if (tab.route == ROUTE_ACTIVITY && metrics.dueCallbacksCount > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge { Text(metrics.dueCallbacksCount.toString()) }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    }
                                },
                                label = { Text(tab.label, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail && !isDetailRoute) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        NAV_TABS.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_DASHBOARD
                    ) {
                        composable(ROUTE_DASHBOARD) {
                            DashboardScreen(
                                agentProfile = agentProfile,
                                metrics = metrics,
                                callListsState = callListsState,
                                leadsState = leadsState,
                                isMutating = isMutating,
                                onStartPowerDialer = {
                                    viewModel.startPowerDialForList(null, autoStartCountdown = false)
                                    navigateToTab(ROUTE_DIALER)
                                },
                                onOpenCreateList = {
                                    importTargetList = null
                                    showImportSheet = true
                                },
                                onLoadStarterCampaign = { viewModel.loadStarterCampaign() },
                                onOpenListDetail = { listId ->
                                    navController.navigate("list_detail/$listId")
                                },
                                onDialLead = { lead ->
                                    viewModel.triggerCallForLead(lead)
                                    navigateToTab(ROUTE_DIALER)
                                },
                                onOpenCallbacksTab = {
                                    activityDefaultHistory = false
                                    navigateToTab(ROUTE_ACTIVITY)
                                }
                            )
                        }

                        composable(ROUTE_LISTS) {
                            CallListsScreen(
                                callListsState = callListsState,
                                onOpenCreateList = {
                                    importTargetList = null
                                    showImportSheet = true
                                },
                                onOpenListDetail = { listId ->
                                    navController.navigate("list_detail/$listId")
                                },
                                onDialList = { listId ->
                                    viewModel.startPowerDialForList(listId, autoStartCountdown = true)
                                    navigateToTab(ROUTE_DIALER)
                                },
                                onDeleteList = { id, name ->
                                    viewModel.deleteCallList(id, name)
                                }
                            )
                        }

                        composable(
                            route = ROUTE_LIST_DETAIL,
                            arguments = listOf(navArgument("listId") { type = NavType.StringType })
                        ) { entry ->
                            val listId = entry.arguments?.getString("listId").orEmpty()
                            val selectedList = allLists.firstOrNull { it.id == listId }
                            if (selectedList == null) {
                                LaunchedEffect(listId) {
                                    navController.popBackStack()
                                }
                            } else {
                                val listLeads = remember(allLeads, listId) {
                                    allLeads.filter { it.listId == listId }
                                }
                                ListDetailScreen(
                                    callList = selectedList,
                                    leads = listLeads,
                                    onBack = { navController.popBackStack() },
                                    onAddLeadsToList = {
                                        importTargetList = selectedList
                                        showImportSheet = true
                                    },
                                    onStartAutoDialer = {
                                        viewModel.startPowerDialForList(listId, autoStartCountdown = true)
                                        navigateToTab(ROUTE_DIALER)
                                    },
                                    onCallLead = { lead ->
                                        viewModel.triggerCallForLead(lead)
                                        navigateToTab(ROUTE_DIALER)
                                    },
                                    onEditLead = { leadId, name, phone, company, email, notes ->
                                        viewModel.updateLeadDetails(leadId, name, phone, company, email, notes)
                                    },
                                    onDeleteLead = { lead -> viewModel.deleteLead(lead) }
                                )
                            }
                        }

                        composable(ROUTE_DIALER) {
                            DialerScreen(
                                dialerState = dialerState,
                                queueLeads = queueLeads,
                                callLists = allLists,
                                templates = allTemplates,
                                autoDialDelaySec = (agentProfile?.autoDialDelaySec ?: 5L).toInt(),
                                onModeSelected = { viewModel.setDialerMode(it) },
                                onSelectList = { viewModel.selectListForDialing(it) },
                                onToggleAutoDial = { viewModel.toggleAutoDial(it) },
                                onStartCountdown = { viewModel.startAutoDialCountdown() },
                                onCancelCountdown = { viewModel.cancelCountdown() },
                                onSkipLead = { viewModel.skipCurrentLead(it) },
                                onCallLeadNow = { viewModel.triggerCallForLead(it) },
                                onEndCallAndLog = { viewModel.openDispositionForCurrentCall() },
                                onManualNumberChange = { viewModel.updateManualNumber(it) },
                                onCallManualNumber = { viewModel.triggerManualCall(it) },
                                onOpenListsTab = { navigateToTab(ROUTE_LISTS) }
                            )
                        }

                        composable(ROUTE_ACTIVITY) {
                            ActivityScreen(
                                leadsState = leadsState,
                                callLogsState = callLogsState,
                                initialShowHistory = activityDefaultHistory,
                                onCallLead = { lead ->
                                    viewModel.triggerCallForLead(lead)
                                    navigateToTab(ROUTE_DIALER)
                                },
                                onCompleteCallback = { viewModel.completeCallback(it) },
                                onRescheduleCallback = { id, millis ->
                                    viewModel.rescheduleCallback(id, millis)
                                },
                                onRedialPhone = { phone ->
                                    viewModel.triggerManualCall(phone)
                                    navigateToTab(ROUTE_DIALER)
                                }
                            )
                        }

                        composable(ROUTE_SETTINGS) {
                            SettingsScreen(
                                agentProfile = agentProfile,
                                templatesState = templatesState,
                                isMutating = isMutating,
                                onSaveSettings = { name, delay, target, code, autoAdvance ->
                                    viewModel.updateSettings(name, delay, target, code, autoAdvance)
                                },
                                onSaveTemplate = { id, title, channel, body ->
                                    viewModel.saveTemplate(id, title, channel, body)
                                },
                                onDeleteTemplate = { id -> viewModel.deleteTemplate(id) },
                                onSignOut = {
                                    signOut(credentialManager, scope)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showImportSheet) {
        LeadImportSheet(
            targetExistingList = importTargetList,
            defaultCountryCode = agentProfile?.defaultCountryCode ?: "+91",
            isSaving = isMutating,
            onDismiss = { showImportSheet = false },
            onCreateListWithLeads = { name, desc, parsed ->
                viewModel.createCallList(name, desc, parsed) {
                    showImportSheet = false
                }
            },
            onAddLeadsToList = { list, parsed ->
                viewModel.addLeadsToExistingList(list, parsed) {
                    showImportSheet = false
                }
            }
        )
    }

    if (dialerState.showDispositionSheet && dialerState.activeCallLead != null) {
        DispositionSheet(
            lead = dialerState.activeCallLead!!,
            callDurationSec = dialerState.callElapsedSec,
            templates = allTemplates,
            isSaving = dialerState.isSavingDisposition,
            onDismiss = { viewModel.dismissDispositionSheet() },
            onSubmit = { disposition, notes, callbackMillis, quickName ->
                viewModel.submitDisposition(
                    disposition = disposition,
                    notes = notes,
                    callbackAtMillis = callbackMillis,
                    saveQuickDialName = quickName
                )
            }
        )
    }
}
