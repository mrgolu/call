package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AgentProfile
import com.example.data.model.CallList
import com.example.data.model.CallLog
import com.example.data.model.Lead
import com.example.data.model.LeadDisposition
import com.example.data.model.MessageTemplate
import com.example.data.model.ParsedCsvLead
import com.example.data.model.TemplateChannel
import com.example.data.model.UiState
import com.example.data.repository.CallFlowRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

enum class DialerMode(val label: String) {
    QUEUE("Power Queue"),
    CALLBACKS("Callbacks"),
    KEYPAD("Keypad")
}

data class DashboardMetrics(
    val callsToday: Int = 0,
    val connectedToday: Int = 0,
    val connectedRatePercent: Int = 0,
    val interestedCount: Int = 0,
    val pendingQueueCount: Int = 0,
    val dueCallbacksCount: Int = 0,
    val totalLeads: Int = 0,
    val dispositionBreakdown: List<Pair<LeadDisposition, Int>> = emptyList()
)

data class DialerState(
    val mode: DialerMode = DialerMode.QUEUE,
    val selectedListId: String? = null,
    val skippedLeadIds: Set<String> = emptySet(),
    val autoDialEnabled: Boolean = true,
    val countdownSeconds: Int? = null,
    val activeCallLead: Lead? = null,
    val isCallInProgress: Boolean = false,
    val callElapsedSec: Long = 0L,
    val showDispositionSheet: Boolean = false,
    val manualNumber: String = "",
    val isSavingDisposition: Boolean = false
)

class CallFlowViewModel(
    private val repository: CallFlowRepository,
    val currentUserId: String,
    initialDisplayName: String,
    initialEmail: String
) : ViewModel() {

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private val _dialLaunchRequest = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val dialLaunchRequest: SharedFlow<String> = _dialLaunchRequest.asSharedFlow()

    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()

    val agentProfile: StateFlow<AgentProfile?> = repository.observeAgentProfile(currentUserId)
        .catch { error ->
            unwrapAndLog(error, "agentProfile")
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    val callListsState: StateFlow<UiState<List<CallList>>> = repository.observeCallLists(currentUserId)
        .toUiStateFlow("call_lists")

    val leadsState: StateFlow<UiState<List<Lead>>> = repository.observeAllLeads(currentUserId)
        .toUiStateFlow("leads")

    val callLogsState: StateFlow<UiState<List<CallLog>>> = repository.observeCallLogs(currentUserId)
        .toUiStateFlow("call_logs")

    val templatesState: StateFlow<UiState<List<MessageTemplate>>> = repository.observeTemplates(currentUserId)
        .toUiStateFlow("templates")

    private val _dialerState = MutableStateFlow(DialerState())
    val dialerState: StateFlow<DialerState> = _dialerState.asStateFlow()

    val dashboardMetrics: StateFlow<DashboardMetrics> = combine(
        leadsState,
        callLogsState
    ) { leadsUi, logsUi ->
        val leads = (leadsUi as? UiState.Success)?.data.orEmpty()
        val logs = (logsUi as? UiState.Success)?.data.orEmpty()

        val startOfTodayMillis = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val todayLogs = logs.filter { (it.createdAt?.toDate()?.time ?: 0L) >= startOfTodayMillis }
        val connectedToday = todayLogs.count { it.leadDisposition.isConnected }
        val rate = if (todayLogs.isEmpty()) 0 else ((connectedToday * 100f) / todayLogs.size).toInt()

        val now = System.currentTimeMillis()
        val dueCallbacks = leads.count {
            it.callbackAtMillis > 0L && !it.callbackCompleted && it.callbackAtMillis <= now + 86_400_000L
        }
        val pendingCount = leads.count { it.disposition == LeadDisposition.PENDING }
        val interestedCount = leads.count { it.disposition == LeadDisposition.INTERESTED }

        val breakdown = LeadDisposition.callableDispositions.mapNotNull { disp ->
            val count = leads.count { it.disposition == disp }
            if (count > 0) disp to count else null
        }.sortedByDescending { it.second }

        DashboardMetrics(
            callsToday = todayLogs.size,
            connectedToday = connectedToday,
            connectedRatePercent = rate,
            interestedCount = interestedCount,
            pendingQueueCount = pendingCount,
            dueCallbacksCount = dueCallbacks,
            totalLeads = leads.size,
            dispositionBreakdown = breakdown
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = DashboardMetrics()
    )

    private var countdownJob: Job? = null
    private var callTimerJob: Job? = null

    init {
        viewModelScope.launch {
            val result = repository.ensureAgentProfile(
                userId = currentUserId,
                displayName = initialDisplayName,
                email = initialEmail
            )
            result.onFailure { err ->
                _toastMessage.tryEmit(err.localizedMessage ?: "Failed to sync agent profile")
            }
        }
    }

    private fun <T> Flow<List<T>>.toUiStateFlow(label: String): StateFlow<UiState<List<T>>> =
        this.map<List<T>, UiState<List<T>>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapAndLog(error, label)
                emit(UiState.Error(root.message ?: "Failed to load $label"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private fun unwrapAndLog(error: Throwable, label: String): Throwable {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing $label", rootError)
        return rootError
    }

    fun getCurrentQueueLeads(): List<Lead> {
        val allLeads = (leadsState.value as? UiState.Success)?.data.orEmpty()
        val state = _dialerState.value
        val filtered = when (state.mode) {
            DialerMode.QUEUE -> {
                allLeads.filter { lead ->
                    val listMatch = state.selectedListId == null || lead.listId == state.selectedListId
                    listMatch && lead.disposition == LeadDisposition.PENDING
                }.reversed() // Oldest/imported order first
            }
            DialerMode.CALLBACKS -> {
                allLeads.filter { it.callbackAtMillis > 0L && !it.callbackCompleted }
                    .sortedBy { it.callbackAtMillis }
            }
            DialerMode.KEYPAD -> emptyList()
        }
        val unskipped = filtered.filterNot { it.id in state.skippedLeadIds }
        return unskipped.ifEmpty { filtered }
    }

    fun setDialerMode(mode: DialerMode) {
        cancelCountdown()
        _dialerState.update { it.copy(mode = mode, skippedLeadIds = emptySet()) }
    }

    fun selectListForDialing(listId: String?) {
        cancelCountdown()
        _dialerState.update {
            it.copy(
                mode = DialerMode.QUEUE,
                selectedListId = listId,
                skippedLeadIds = emptySet()
            )
        }
    }

    fun startPowerDialForList(listId: String?, autoStartCountdown: Boolean = true) {
        cancelCountdown()
        _dialerState.update {
            it.copy(
                mode = DialerMode.QUEUE,
                selectedListId = listId,
                skippedLeadIds = emptySet(),
                autoDialEnabled = true
            )
        }
        if (autoStartCountdown) {
            startAutoDialCountdown()
        }
    }

    fun toggleAutoDial(enabled: Boolean) {
        if (!enabled) cancelCountdown()
        _dialerState.update { it.copy(autoDialEnabled = enabled) }
    }

    fun startAutoDialCountdown() {
        val queue = getCurrentQueueLeads()
        val nextLead = queue.firstOrNull() ?: return
        val delaySec = (agentProfile.value?.autoDialDelaySec ?: 5L).toInt().coerceIn(2, 30)
        cancelCountdown()
        countdownJob = viewModelScope.launch {
            for (sec in delaySec downTo 1) {
                _dialerState.update { it.copy(countdownSeconds = sec) }
                delay(1000L)
            }
            _dialerState.update { it.copy(countdownSeconds = null) }
            triggerCallForLead(nextLead)
        }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _dialerState.update { it.copy(countdownSeconds = null) }
    }

    fun skipCurrentLead(leadId: String) {
        cancelCountdown()
        _dialerState.update { state ->
            val allQueue = getCurrentQueueLeads()
            val updatedSkipped = if (allQueue.size <= 1) emptySet() else state.skippedLeadIds + leadId
            state.copy(skippedLeadIds = updatedSkipped)
        }
    }

    fun triggerCallForLead(lead: Lead) {
        cancelCountdown()
        startCallTimer(lead)
        _dialLaunchRequest.tryEmit(lead.phoneNumber)
    }

    fun triggerManualCall(phoneNumber: String) {
        val trimmed = phoneNumber.trim()
        if (trimmed.length < 3) {
            _toastMessage.tryEmit("Enter a valid phone number")
            return
        }
        val adHocLead = Lead(
            id = "",
            userId = currentUserId,
            listId = "",
            listName = "Quick Dial",
            fullName = "Quick Dial ($trimmed)",
            phoneNumber = trimmed
        )
        startCallTimer(adHocLead)
        _dialLaunchRequest.tryEmit(trimmed)
    }

    private fun startCallTimer(lead: Lead) {
        callTimerJob?.cancel()
        _dialerState.update {
            it.copy(
                activeCallLead = lead,
                isCallInProgress = true,
                callElapsedSec = 0L,
                showDispositionSheet = false
            )
        }
        callTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                _dialerState.update { it.copy(callElapsedSec = it.callElapsedSec + 1L) }
            }
        }
    }

    fun openDispositionForCurrentCall() {
        callTimerJob?.cancel()
        callTimerJob = null
        _dialerState.update {
            it.copy(
                isCallInProgress = false,
                showDispositionSheet = it.activeCallLead != null
            )
        }
    }

    fun dismissDispositionSheet() {
        _dialerState.update {
            it.copy(
                showDispositionSheet = false,
                activeCallLead = null,
                isCallInProgress = false
            )
        }
    }

    fun updateManualNumber(number: String) {
        _dialerState.update { it.copy(manualNumber = number.take(24)) }
    }

    fun submitDisposition(
        disposition: LeadDisposition,
        notes: String,
        callbackAtMillis: Long,
        saveQuickDialName: String? = null
    ) {
        val currentLead = _dialerState.value.activeCallLead ?: return
        val duration = _dialerState.value.callElapsedSec
        viewModelScope.launch {
            _dialerState.update { it.copy(isSavingDisposition = true) }

            // Handle ad-hoc Quick Dial lead: save into a Quick Dial list if requested or dismiss cleanly
            if (currentLead.id.isBlank()) {
                if (!saveQuickDialName.isNullOrBlank()) {
                    val lists = (callListsState.value as? UiState.Success)?.data.orEmpty()
                    val existingQuickList = lists.firstOrNull { it.name == "Quick Dial Contacts" }
                    val parsed = ParsedCsvLead(
                        fullName = saveQuickDialName.trim(),
                        phoneNumber = currentLead.phoneNumber,
                        notes = notes
                    )
                    if (existingQuickList != null) {
                        repository.addBulkLeadsToList(currentUserId, existingQuickList, listOf(parsed))
                    } else {
                        repository.createCallListWithLeads(
                            currentUserId,
                            "Quick Dial Contacts",
                            "Saved from manual keypad",
                            listOf(parsed)
                        )
                    }
                    _toastMessage.tryEmit("Saved ${parsed.fullName} to Quick Dial Contacts")
                } else {
                    _toastMessage.tryEmit("Call logged (${disposition.label})")
                }
                _dialerState.update {
                    it.copy(
                        isSavingDisposition = false,
                        showDispositionSheet = false,
                        activeCallLead = null,
                        callElapsedSec = 0L
                    )
                }
                return@launch
            }

            val lists = (callListsState.value as? UiState.Success)?.data.orEmpty()
            val parentList = lists.firstOrNull { it.id == currentLead.listId }

            val result = repository.recordCallDisposition(
                userId = currentUserId,
                lead = currentLead,
                parentList = parentList,
                disposition = disposition,
                notes = notes,
                durationSec = duration,
                callbackAtMillis = callbackAtMillis
            )

            result.onSuccess {
                _toastMessage.tryEmit("Logged: ${disposition.label}")
                val shouldAutoAdvance = _dialerState.value.autoDialEnabled &&
                    (agentProfile.value?.autoAdvanceDialer ?: true) &&
                    _dialerState.value.mode != DialerMode.KEYPAD
                _dialerState.update {
                    it.copy(
                        isSavingDisposition = false,
                        showDispositionSheet = false,
                        activeCallLead = null,
                        callElapsedSec = 0L
                    )
                }
                if (shouldAutoAdvance) {
                    delay(450L)
                    startAutoDialCountdown()
                }
            }.onFailure { err ->
                _dialerState.update { it.copy(isSavingDisposition = false) }
                _toastMessage.tryEmit(err.localizedMessage ?: "Failed to save disposition")
            }
        }
    }

    fun createCallList(name: String, description: String, leads: List<ParsedCsvLead>, onSuccess: (String) -> Unit = {}) {
        viewModelScope.launch {
            _isMutating.value = true
            val res = repository.createCallListWithLeads(currentUserId, name, description, leads)
            _isMutating.value = false
            res.onSuccess { listId ->
                _toastMessage.tryEmit("Created \"$name\" with ${leads.size} leads")
                onSuccess(listId)
            }.onFailure { err ->
                _toastMessage.tryEmit(err.localizedMessage ?: "Failed to create call list")
            }
        }
    }

    fun addLeadsToExistingList(list: CallList, leads: List<ParsedCsvLead>, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isMutating.value = true
            val res = repository.addBulkLeadsToList(currentUserId, list, leads)
            _isMutating.value = false
            res.onSuccess { count ->
                _toastMessage.tryEmit("Added $count leads to ${list.name}")
                onSuccess()
            }.onFailure { err ->
                _toastMessage.tryEmit(err.localizedMessage ?: "Failed to add leads")
            }
        }
    }

    fun updateLeadDetails(
        leadId: String,
        fullName: String,
        phoneNumber: String,
        company: String,
        email: String,
        notes: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isMutating.value = true
            val res = repository.updateLeadDetails(currentUserId, leadId, fullName, phoneNumber, company, email, notes)
            _isMutating.value = false
            res.onSuccess {
                _toastMessage.tryEmit("Lead updated")
                onSuccess()
            }.onFailure { err ->
                _toastMessage.tryEmit(err.localizedMessage ?: "Failed to update lead")
            }
        }
    }

    fun deleteLead(lead: Lead) {
        viewModelScope.launch {
            val lists = (callListsState.value as? UiState.Success)?.data.orEmpty()
            val parent = lists.firstOrNull { it.id == lead.listId }
            val res = repository.deleteLead(currentUserId, lead, parent)
            res.onSuccess { _toastMessage.tryEmit("Deleted ${lead.fullName}") }
                .onFailure { _toastMessage.tryEmit(it.localizedMessage ?: "Failed to delete lead") }
        }
    }

    fun deleteCallList(listId: String, name: String) {
        viewModelScope.launch {
            _isMutating.value = true
            val res = repository.deleteCallList(currentUserId, listId)
            _isMutating.value = false
            res.onSuccess {
                if (_dialerState.value.selectedListId == listId) {
                    _dialerState.update { it.copy(selectedListId = null) }
                }
                _toastMessage.tryEmit("Deleted list \"$name\"")
            }.onFailure {
                _toastMessage.tryEmit(it.localizedMessage ?: "Failed to delete list")
            }
        }
    }

    fun completeCallback(leadId: String) {
        viewModelScope.launch {
            repository.completeCallback(currentUserId, leadId)
                .onSuccess { _toastMessage.tryEmit("Callback marked completed") }
                .onFailure { _toastMessage.tryEmit(it.localizedMessage ?: "Failed to update callback") }
        }
    }

    fun rescheduleCallback(leadId: String, newMillis: Long) {
        viewModelScope.launch {
            repository.rescheduleCallback(currentUserId, leadId, newMillis)
                .onSuccess { _toastMessage.tryEmit("Callback rescheduled") }
                .onFailure { _toastMessage.tryEmit(it.localizedMessage ?: "Failed to reschedule") }
        }
    }

    fun updateSettings(
        displayName: String,
        autoDialDelaySec: Long,
        dailyCallTarget: Long,
        defaultCountryCode: String,
        autoAdvanceDialer: Boolean
    ) {
        viewModelScope.launch {
            _isMutating.value = true
            val res = repository.updateAgentSettings(
                currentUserId,
                displayName,
                autoDialDelaySec,
                dailyCallTarget,
                defaultCountryCode,
                autoAdvanceDialer
            )
            _isMutating.value = false
            res.onSuccess { _toastMessage.tryEmit("Dialer settings saved") }
                .onFailure { _toastMessage.tryEmit(it.localizedMessage ?: "Failed to save settings") }
        }
    }

    fun saveTemplate(templateId: String?, title: String, channel: TemplateChannel, body: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isMutating.value = true
            val res = repository.saveTemplate(currentUserId, templateId, title, channel, body)
            _isMutating.value = false
            res.onSuccess {
                _toastMessage.tryEmit("Template saved")
                onSuccess()
            }.onFailure {
                _toastMessage.tryEmit(it.localizedMessage ?: "Failed to save template")
            }
        }
    }

    fun deleteTemplate(templateId: String) {
        viewModelScope.launch {
            repository.deleteTemplate(currentUserId, templateId)
                .onSuccess { _toastMessage.tryEmit("Template deleted") }
                .onFailure { _toastMessage.tryEmit(it.localizedMessage ?: "Failed to delete template") }
        }
    }

    fun loadStarterCampaign() {
        viewModelScope.launch {
            _isMutating.value = true
            val res = repository.loadStarterCampaign(currentUserId)
            _isMutating.value = false
            res.onSuccess {
                _toastMessage.tryEmit("Starter campaign added with 5 leads!")
            }.onFailure {
                _toastMessage.tryEmit(it.localizedMessage ?: "Failed to load starter campaign")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
        callTimerJob?.cancel()
    }

    private companion object {
        const val TAG = "CallFlowVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
