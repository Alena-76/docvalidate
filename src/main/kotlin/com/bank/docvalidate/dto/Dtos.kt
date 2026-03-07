package com.bank.docvalidate.dto

import com.bank.docvalidate.entity.Document
import com.bank.docvalidate.entity.DocumentAuditLog
import com.bank.docvalidate.enums.DocumentStatus
import com.bank.docvalidate.enums.DocumentType
import jakarta.validation.constraints.*
import java.time.Instant
import java.time.LocalDate

// ─────────────────────────────────────────────────────────────────────────────
// AUTH
// ─────────────────────────────────────────────────────────────────────────────

/**
 * WHY DTOs? Never expose your @Entity directly in the API. Reasons:
 *  1. Entities may have sensitive fields (hashed password)
 *  2. API shape shouldn't be coupled to DB schema
 *  3. You can add @Valid annotations without touching the entity
 */

data class LoginRequest(
    @field:NotBlank(message = "Username is required")
    val username: String,

    @field:NotBlank(message = "Password is required")
    val password: String
)

data class LoginResponse(
    val token: String,
    val username: String,
    val fullName: String,
    val roles: Set<String>
)

data class RegisterRequest(
    @field:NotBlank @field:Size(min = 3, max = 50)
    val username: String,

    @field:NotBlank @field:Size(min = 8, message = "Password must be at least 8 characters")
    val password: String,

    @field:NotBlank @field:Size(max = 100)
    val fullName: String,

    @field:Email @field:NotBlank
    val email: String
)

// ─────────────────────────────────────────────────────────────────────────────
// DOCUMENT
// ─────────────────────────────────────────────────────────────────────────────

/** Used when submitting a new document (multipart form fields) */
data class DocumentSubmitRequest(
    @field:NotBlank(message = "Customer ID is required")
    val customerId: String,

    @field:NotBlank @field:Size(max = 100)
    val customerName: String,

    @field:NotNull(message = "Document type is required")
    val documentType: DocumentType,

    val documentExpiryDate: LocalDate? = null,

    val firstLevelNotes: String? = null
)

/** Full document response — sent back to clients */
data class DocumentResponse(
    val id: Long,
    val customerId: String,
    val customerName: String,
    val documentType: String,
    val documentExpiryDate: LocalDate?,
    val originalFileName: String,
    val fileSizeBytes: Long,
    val status: DocumentStatus,
    val firstLevelNotes: String?,
    val secondLevelNotes: String?,
    val rejectionReason: String?,
    val escalationReason: String?,
    val assignedReviewerName: String?,
    val submittedAt: Instant,
    val reviewStartedAt: Instant?,
    val completedAt: Instant?,
    val auditLogs: List<AuditLogResponse>
) {
    companion object {
        /** Extension function pattern: converts entity → DTO cleanly */
        fun from(doc: Document) = DocumentResponse(
            id = doc.id,
            customerId = doc.customerId,
            customerName = doc.customerName,
            documentType = doc.documentType.displayName,
            documentExpiryDate = doc.documentExpiryDate,
            originalFileName = doc.originalFileName,
            fileSizeBytes = doc.fileSizeBytes,
            status = doc.status,
            firstLevelNotes = doc.firstLevelNotes,
            secondLevelNotes = doc.secondLevelNotes,
            rejectionReason = doc.rejectionReason,
            escalationReason = doc.escalationReason,
            assignedReviewerName = doc.assignedReviewer?.fullName,
            submittedAt = doc.submittedAt,
            reviewStartedAt = doc.reviewStartedAt,
            completedAt = doc.completedAt,
            auditLogs = doc.auditLogs.map { AuditLogResponse.from(it) }
        )
    }
}

/** Lightweight summary for lists/queues */
data class DocumentSummary(
    val id: Long,
    val customerId: String,
    val customerName: String,
    val documentType: String,
    val status: DocumentStatus,
    val submittedAt: Instant,
    val assignedReviewerName: String?
) {
    companion object {
        fun from(doc: Document) = DocumentSummary(
            id = doc.id,
            customerId = doc.customerId,
            customerName = doc.customerName,
            documentType = doc.documentType.displayName,
            status = doc.status,
            submittedAt = doc.submittedAt,
            assignedReviewerName = doc.assignedReviewer?.fullName
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// REVIEW ACTIONS
// ─────────────────────────────────────────────────────────────────────────────

data class ReviewDecisionRequest(
    @field:NotNull(message = "Decision is required")
    val decision: ReviewDecision,

    val notes: String? = null,

    // Required only when decision = REJECTED
    val rejectionReason: String? = null,

    // Required only when decision = ESCALATED
    val escalationReason: String? = null
)

enum class ReviewDecision { APPROVE, REJECT, ESCALATE }

// ─────────────────────────────────────────────────────────────────────────────
// AUDIT LOG
// ─────────────────────────────────────────────────────────────────────────────

data class AuditLogResponse(
    val id: Long,
    val previousStatus: DocumentStatus,
    val newStatus: DocumentStatus,
    val performedBy: String,
    val notes: String?,
    val performedAt: Instant
) {
    companion object {
        fun from(log: DocumentAuditLog) = AuditLogResponse(
            id = log.id,
            previousStatus = log.previousStatus,
            newStatus = log.newStatus,
            performedBy = log.performedBy,
            notes = log.notes,
            performedAt = log.performedAt
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DASHBOARD
// ─────────────────────────────────────────────────────────────────────────────

data class DashboardStats(
    val totalDocuments: Long,
    val pendingSecondLevel: Long,
    val underReview: Long,
    val approvedToday: Long,
    val rejectedToday: Long,
    val escalated: Long
)

// ─────────────────────────────────────────────────────────────────────────────
// API WRAPPER
// ─────────────────────────────────────────────────────────────────────────────

/** Generic success wrapper — keeps API responses consistent */
data class ApiResponse<T>(
    val success: Boolean = true,
    val message: String? = null,
    val data: T? = null
)
