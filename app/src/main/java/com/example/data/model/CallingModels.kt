package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ListStatus(val label: String) {
    ACTIVE("Active"),
    PAUSED("Paused"),
    COMPLETED("Completed");

    companion object {
        fun fromRaw(raw: String): ListStatus =
            entries.firstOrNull { it.name == raw } ?: ACTIVE
    }
}

enum class LeadDisposition(val label: String, val isConnected: Boolean) {
    PENDING("Pending", false),
    INTERESTED("Interested", true),
    FOLLOW_UP("Follow-Up", true),
    CALLBACK("Callback", true),
    NOT_ANSWERED("Not Answered", false),
    BUSY("Busy", false),
    WRONG_NUMBER("Wrong Number", false),
    NOT_INTERESTED("Not Interested", true),
    DND("DND", true);

    companion object {
        val callableDispositions: List<LeadDisposition> = listOf(
            INTERESTED,
            FOLLOW_UP,
            CALLBACK,
            NOT_ANSWERED,
            BUSY,
            WRONG_NUMBER,
            NOT_INTERESTED,
            DND
        )

        fun fromRaw(raw: String): LeadDisposition =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class TemplateChannel(val label: String) {
    WHATSAPP("WhatsApp"),
    SMS("SMS"),
    SCRIPT("Call Script");

    companion object {
        fun fromRaw(raw: String): TemplateChannel =
            entries.firstOrNull { it.name == raw } ?: WHATSAPP
    }
}

@Keep
@IgnoreExtraProperties
data class AgentProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val autoDialDelaySec: Long = 5L,
    val dailyCallTarget: Long = 50L,
    val defaultCountryCode: String = "+91",
    val autoAdvanceDialer: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CallList(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val totalLeads: Long = 0L,
    val dialedLeads: Long = 0L,
    val status: String = ListStatus.ACTIVE.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val listStatus: ListStatus
        get() = ListStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class Lead(
    @DocumentId val id: String = "",
    val userId: String = "",
    val listId: String = "",
    val listName: String = "",
    val fullName: String = "",
    val phoneNumber: String = "",
    val company: String = "",
    val email: String = "",
    val status: String = LeadDisposition.PENDING.name,
    val lastDisposition: String = "",
    val notes: String = "",
    val callAttempts: Long = 0L,
    val lastDurationSec: Long = 0L,
    val callbackAtMillis: Long = 0L,
    val callbackCompleted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val disposition: LeadDisposition
        get() = LeadDisposition.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class CallLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val leadId: String = "",
    val listId: String = "",
    val leadName: String = "",
    val phoneNumber: String = "",
    val disposition: String = LeadDisposition.NOT_ANSWERED.name,
    val notes: String = "",
    val durationSec: Long = 0L,
    val callbackAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val leadDisposition: LeadDisposition
        get() = LeadDisposition.fromRaw(disposition)
}

@Keep
@IgnoreExtraProperties
data class MessageTemplate(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val channel: String = TemplateChannel.WHATSAPP.name,
    val body: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val templateChannel: TemplateChannel
        get() = TemplateChannel.fromRaw(channel)
}

data class ParsedCsvLead(
    val fullName: String,
    val phoneNumber: String,
    val company: String = "",
    val email: String = "",
    val notes: String = ""
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
