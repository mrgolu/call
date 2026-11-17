package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AgentProfile
import com.example.data.model.CallList
import com.example.data.model.CallLog
import com.example.data.model.Lead
import com.example.data.model.LeadDisposition
import com.example.data.model.ListStatus
import com.example.data.model.MessageTemplate
import com.example.data.model.ParsedCsvLead
import com.example.data.model.TemplateChannel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CallFlowRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Please sign in with Google to continue.")
        if (expectedUserId != null) {
            check(uid == expectedUserId) { "Authenticated user changed; refusing cross-user access." }
        }
        return uid
    }

    private fun userDoc(userId: String) = db.collection("users").document(userId)
    private fun listsCollection(userId: String) = userDoc(userId).collection("call_lists")
    private fun leadsCollection(userId: String) = userDoc(userId).collection("leads")
    private fun logsCollection(userId: String) = userDoc(userId).collection("call_logs")
    private fun templatesCollection(userId: String) = userDoc(userId).collection("templates")

    fun observeAgentProfile(userId: String): Flow<AgentProfile?> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid"
        emitAll(
            userDoc(uid).snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            AgentProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    fun observeCallLists(userId: String): Flow<List<CallList>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/call_lists"
        emitAll(
            listsCollection(uid).snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CallList::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeAllLeads(userId: String): Flow<List<Lead>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/leads"
        emitAll(
            leadsCollection(uid).snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Lead::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeCallLogs(userId: String): Flow<List<CallLog>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/call_logs"
        emitAll(
            logsCollection(uid).snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CallLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeTemplates(userId: String): Flow<List<MessageTemplate>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/templates"
        emitAll(
            templatesCollection(uid).snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MessageTemplate::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    private fun logStreamErrorIfActive(
        error: Throwable,
        op: OperationType,
        path: String,
        uid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
    }

    suspend fun ensureAgentProfile(
        userId: String,
        displayName: String,
        email: String
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val snap = userDoc(uid).get().await()
            if (!snap.exists()) {
                val cleanName = displayName.trim().ifBlank { "Sales Agent" }.take(80)
                val cleanEmail = email.trim().take(120)
                val profileMap = mapOf(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "email" to cleanEmail,
                    "autoDialDelaySec" to 5L,
                    "dailyCallTarget" to 50L,
                    "defaultCountryCode" to "+91",
                    "autoAdvanceDialer" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userDoc(uid).set(profileMap).await()
                seedDefaultTemplatesIfEmpty(uid)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private suspend fun seedDefaultTemplatesIfEmpty(uid: String) {
        val existing = templatesCollection(uid).limit(1).get().await()
        if (!existing.isEmpty) return
        val defaults = listOf(
            Triple(
                "Quick Intro Follow-Up",
                TemplateChannel.WHATSAPP.name,
                "Hi {name}, thanks for taking my call today! As discussed, I am sharing our quick overview. Let me know a good time to connect again."
            ),
            Triple(
                "Missed Call Check-In",
                TemplateChannel.SMS.name,
                "Hi {name}, tried reaching you regarding your inquiry. Please let us know when you are available for a quick 2-minute call."
            ),
            Triple(
                "Outbound Pitch Script",
                TemplateChannel.SCRIPT.name,
                "Hello {name}, I am calling from CallFlow Solutions. Is now a quick 60 seconds okay to discuss how we can streamline your workflow?"
            )
        )
        val batch = db.batch()
        defaults.forEach { (title, channel, body) ->
            val ref = templatesCollection(uid).document()
            batch.set(
                ref,
                mapOf(
                    "userId" to uid,
                    "title" to title,
                    "channel" to channel,
                    "body" to body,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        batch.commit().await()
    }

    suspend fun updateAgentSettings(
        userId: String,
        displayName: String,
        autoDialDelaySec: Long,
        dailyCallTarget: Long,
        defaultCountryCode: String,
        autoAdvanceDialer: Boolean
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val cleanName = displayName.trim().take(80)
            require(cleanName.isNotEmpty()) { "Display name cannot be empty." }
            val cleanCode = defaultCountryCode.trim().take(6)
            require(cleanCode.isNotEmpty()) { "Country code cannot be empty." }
            val safeDelay = autoDialDelaySec.coerceIn(0L, 30L)
            val safeTarget = dailyCallTarget.coerceIn(1L, 500L)
            userDoc(uid).update(
                mapOf(
                    "displayName" to cleanName,
                    "autoDialDelaySec" to safeDelay,
                    "dailyCallTarget" to safeTarget,
                    "defaultCountryCode" to cleanCode,
                    "autoAdvanceDialer" to autoAdvanceDialer,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun createCallListWithLeads(
        userId: String,
        name: String,
        description: String,
        leads: List<ParsedCsvLead>
    ): Result<String> {
        var path = "users/$userId/call_lists"
        return try {
            val uid = requireUserId(userId)
            val cleanName = name.trim().take(80)
            require(cleanName.isNotEmpty()) { "Campaign name cannot be empty." }
            val cleanDesc = description.trim().take(240)
            val validLeads = leads.filter { it.phoneNumber.trim().length in 3..24 }

            val listRef = listsCollection(uid).document()
            path = listRef.path
            val listId = listRef.id

            val listPayload = mapOf(
                "userId" to uid,
                "name" to cleanName,
                "description" to cleanDesc,
                "totalLeads" to validLeads.size.toLong(),
                "dialedLeads" to 0L,
                "status" to ListStatus.ACTIVE.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            listRef.set(listPayload).await()

            validLeads.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { parsed ->
                    val leadRef = leadsCollection(uid).document()
                    batch.set(
                        leadRef,
                        buildLeadCreateMap(
                            uid = uid,
                            listId = listId,
                            listName = cleanName,
                            parsed = parsed
                        )
                    )
                }
                batch.commit().await()
            }

            Result.success(listId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun addBulkLeadsToList(
        userId: String,
        list: CallList,
        leads: List<ParsedCsvLead>
    ): Result<Int> {
        val path = "users/$userId/leads"
        return try {
            val uid = requireUserId(userId)
            require(list.id.isNotBlank()) { "Call list ID is missing." }
            val validLeads = leads.filter { it.phoneNumber.trim().length in 3..24 }
            require(validLeads.isNotEmpty()) { "No valid phone numbers found to add." }

            validLeads.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { parsed ->
                    val leadRef = leadsCollection(uid).document()
                    batch.set(
                        leadRef,
                        buildLeadCreateMap(
                            uid = uid,
                            listId = list.id,
                            listName = list.name.take(80).ifBlank { "Campaign" },
                            parsed = parsed
                        )
                    )
                }
                batch.commit().await()
            }

            val updatedTotal = (list.totalLeads + validLeads.size).coerceAtMost(50000L)
            listsCollection(uid).document(list.id).update(
                mapOf(
                    "totalLeads" to updatedTotal,
                    "status" to ListStatus.ACTIVE.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(validLeads.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateLeadDetails(
        userId: String,
        leadId: String,
        fullName: String,
        phoneNumber: String,
        company: String,
        email: String,
        notes: String
    ): Result<Unit> {
        val path = "users/$userId/leads/$leadId"
        return try {
            val uid = requireUserId(userId)
            require(leadId.isNotBlank()) { "Lead ID is blank." }
            val cleanName = fullName.trim().take(80)
            require(cleanName.isNotEmpty()) { "Lead name cannot be empty." }
            val cleanPhone = phoneNumber.trim().take(24)
            require(cleanPhone.length >= 3) { "Enter a valid phone number." }

            leadsCollection(uid).document(leadId).update(
                mapOf(
                    "fullName" to cleanName,
                    "phoneNumber" to cleanPhone,
                    "company" to company.trim().take(80),
                    "email" to email.trim().take(120),
                    "notes" to notes.trim().take(500),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteLead(userId: String, lead: Lead, parentList: CallList?): Result<Unit> {
        val path = "users/$userId/leads/${lead.id}"
        return try {
            val uid = requireUserId(userId)
            require(lead.id.isNotBlank()) { "Lead ID is blank." }
            leadsCollection(uid).document(lead.id).delete().await()
            if (parentList != null && parentList.id.isNotBlank()) {
                val newTotal = (parentList.totalLeads - 1L).coerceAtLeast(0L)
                val wasDialed = lead.disposition != LeadDisposition.PENDING
                val newDialed = if (wasDialed) {
                    (parentList.dialedLeads - 1L).coerceAtLeast(0L)
                } else {
                    parentList.dialedLeads
                }.coerceAtMost(newTotal)
                listsCollection(uid).document(parentList.id).update(
                    mapOf(
                        "totalLeads" to newTotal,
                        "dialedLeads" to newDialed,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteCallList(userId: String, listId: String): Result<Unit> {
        val path = "users/$userId/call_lists/$listId"
        return try {
            val uid = requireUserId(userId)
            require(listId.isNotBlank()) { "List ID is blank." }
            val matchingLeads = leadsCollection(uid).whereEqualTo("listId", listId).get().await()
            matchingLeads.documents.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().await()
            }
            listsCollection(uid).document(listId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun recordCallDisposition(
        userId: String,
        lead: Lead,
        parentList: CallList?,
        disposition: LeadDisposition,
        notes: String,
        durationSec: Long,
        callbackAtMillis: Long
    ): Result<Unit> {
        val path = "users/$userId/leads/${lead.id}"
        return try {
            val uid = requireUserId(userId)
            require(lead.id.isNotBlank()) { "Lead ID is blank." }
            require(disposition != LeadDisposition.PENDING) { "Select a call outcome disposition." }

            val cleanNotes = notes.trim().take(500)
            val safeDuration = durationSec.coerceIn(0L, 86400L)
            val safeCallback = if (callbackAtMillis > 0L) {
                callbackAtMillis.coerceIn(0L, 4102444800000L)
            } else {
                0L
            }
            val callbackDone = safeCallback == 0L
            val newAttempts = (lead.callAttempts + 1L).coerceAtMost(1000L)

            val batch = db.batch()

            // 1. Update Lead
            val leadRef = leadsCollection(uid).document(lead.id)
            batch.update(
                leadRef,
                mapOf(
                    "status" to disposition.name,
                    "lastDisposition" to disposition.label.take(40),
                    "notes" to cleanNotes,
                    "callAttempts" to newAttempts,
                    "lastDurationSec" to safeDuration,
                    "callbackAtMillis" to safeCallback,
                    "callbackCompleted" to callbackDone,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            // 2. Append immutable CallLog
            val logRef = logsCollection(uid).document()
            batch.set(
                logRef,
                mapOf(
                    "userId" to uid,
                    "leadId" to lead.id,
                    "listId" to lead.listId,
                    "leadName" to lead.fullName.take(80).ifBlank { "Prospect" },
                    "phoneNumber" to lead.phoneNumber.take(24),
                    "disposition" to disposition.name,
                    "notes" to cleanNotes,
                    "durationSec" to safeDuration,
                    "callbackAtMillis" to safeCallback,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            // 3. Increment CallList dialedLeads if transitioning from PENDING
            if (parentList != null && parentList.id.isNotBlank() && lead.disposition == LeadDisposition.PENDING) {
                val newDialed = (parentList.dialedLeads + 1L).coerceAtMost(parentList.totalLeads.coerceAtLeast(1L))
                val newStatus = if (newDialed >= parentList.totalLeads && parentList.totalLeads > 0L) {
                    ListStatus.COMPLETED.name
                } else {
                    parentList.status
                }
                batch.update(
                    listsCollection(uid).document(parentList.id),
                    mapOf(
                        "dialedLeads" to newDialed,
                        "status" to newStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun completeCallback(userId: String, leadId: String): Result<Unit> {
        val path = "users/$userId/leads/$leadId"
        return try {
            val uid = requireUserId(userId)
            leadsCollection(uid).document(leadId).update(
                mapOf(
                    "callbackAtMillis" to 0L,
                    "callbackCompleted" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun rescheduleCallback(
        userId: String,
        leadId: String,
        callbackAtMillis: Long
    ): Result<Unit> {
        val path = "users/$userId/leads/$leadId"
        return try {
            val uid = requireUserId(userId)
            val safeCallback = callbackAtMillis.coerceIn(0L, 4102444800000L)
            leadsCollection(uid).document(leadId).update(
                mapOf(
                    "callbackAtMillis" to safeCallback,
                    "callbackCompleted" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveTemplate(
        userId: String,
        templateId: String?,
        title: String,
        channel: TemplateChannel,
        body: String
    ): Result<Unit> {
        val path = "users/$userId/templates"
        return try {
            val uid = requireUserId(userId)
            val cleanTitle = title.trim().take(60)
            val cleanBody = body.trim().take(1000)
            require(cleanTitle.isNotEmpty()) { "Template title cannot be empty." }
            require(cleanBody.isNotEmpty()) { "Template message cannot be empty." }

            if (templateId.isNullOrBlank()) {
                templatesCollection(uid).document().set(
                    mapOf(
                        "userId" to uid,
                        "title" to cleanTitle,
                        "channel" to channel.name,
                        "body" to cleanBody,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                templatesCollection(uid).document(templateId).update(
                    mapOf(
                        "title" to cleanTitle,
                        "channel" to channel.name,
                        "body" to cleanBody,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteTemplate(userId: String, templateId: String): Result<Unit> {
        val path = "users/$userId/templates/$templateId"
        return try {
            val uid = requireUserId(userId)
            templatesCollection(uid).document(templateId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun loadStarterCampaign(userId: String): Result<String> {
        val starterLeads = listOf(
            ParsedCsvLead("Aarav Sharma", "+919876543210", "Apex Infra", "aarav@apexinfra.in", "Interested in enterprise calling plan"),
            ParsedCsvLead("Priya Nair", "+919820112233", "FinEdge Advisors", "priya@finedge.co", "Requested pricing callback"),
            ParsedCsvLead("Vikram Malhotra", "+919911445566", "Nova Retail", "vikram@novaretail.in", "Inbound trial signup"),
            ParsedCsvLead("Sneha Patel", "+919725098765", "CloudWave Tech", "sneha@cloudwave.io", "Looking for 10-seat team dialer"),
            ParsedCsvLead("Rohan Verma", "+919811223344", "Metro Logistics", "rohan@metrolog.in", "Follow up on demo request")
        )
        return createCallListWithLeads(
            userId = userId,
            name = "Starter Inbound Leads",
            description = "Sample outbound campaign to test the Power Dialer queue",
            leads = starterLeads
        )
    }

    private fun buildLeadCreateMap(
        uid: String,
        listId: String,
        listName: String,
        parsed: ParsedCsvLead
    ): Map<String, Any> = mapOf(
        "userId" to uid,
        "listId" to listId,
        "listName" to listName.take(80).ifBlank { "Campaign" },
        "fullName" to parsed.fullName.trim().take(80).ifBlank { "Prospect" },
        "phoneNumber" to parsed.phoneNumber.trim().take(24),
        "company" to parsed.company.trim().take(80),
        "email" to parsed.email.trim().take(120),
        "status" to LeadDisposition.PENDING.name,
        "lastDisposition" to "",
        "notes" to parsed.notes.trim().take(500),
        "callAttempts" to 0L,
        "lastDurationSec" to 0L,
        "callbackAtMillis" to 0L,
        "callbackCompleted" to false,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
