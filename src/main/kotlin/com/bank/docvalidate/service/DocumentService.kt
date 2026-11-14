package com.bank.docvalidate.service

import com.bank.docvalidate.dto.*
import com.bank.docvalidate.entity.Document
import com.bank.docvalidate.entity.DocumentAuditLog
import com.bank.docvalidate.enums.DocumentStatus
import com.bank.docvalidate.exception.BadRequestException
import com.bank.docvalidate.exception.ResourceNotFoundException
import com.bank.docvalidate.repository.DocumentAuditLogRepository
import com.bank.docvalidate.repository.DocumentRepository
import com.bank.docvalidate.repository.UserRepository
import com.bank.docvalidate.security.UserDetailsImpl
import org.springframework.core.io.Resource
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.Instant

/**
 * Core business logic for the document review workflow.
 *
 * KEY CONCEPTS demonstrated here:
 *
 * 1. @Transactional — every public method that writes to DB is wrapped in a transaction.
 *    If anything throws, Spring rolls back ALL changes in that transaction.
 *    readOnly = true is a hint to Hibernate/DB to skip dirty-checking (optimization).
 *
 * 2. @PreAuthorize — method-level security. Spring checks the user's roles BEFORE
 *    calling the method. If not authorized, throws AccessDeniedException → 403.
 *    Requires @EnableMethodSecurity in SecurityConfig.
 *
 * 3. SecurityContextHolder — the "thread-local store" for the current user's auth info.
 *    Populated by JwtAuthenticationFilter on each request.
 *
 * 4. State Machine pattern — the status transitions are enforced here, not in the entity.
 *    This keeps business rules centralized and testable.
 */
@Service
@Transactional
class DocumentService(
    private val documentRepository: DocumentRepository,
    private val auditLogRepository: DocumentAuditLogRepository,
    private val userRepository: UserRepository,
    private val fileStorageService: FileStorageService
) {

    // ─── Submission ────────────────────────────────────────────────────────

    fun submitDocument(request: DocumentSubmitRequest, file: MultipartFile): DocumentResponse {
        val storedFile = fileStorageService.store(file, request.documentType)

        val document = Document(
            customerId = request.customerId,
            customerName = request.customerName,
            documentType = request.documentType,
            documentExpiryDate = request.documentExpiryDate,
            originalFileName = storedFile.originalFileName,
            storedFileName = storedFile.storedFileName,
            fileExtension = storedFile.extension,
            fileSizeBytes = storedFile.sizeBytes,
            firstLevelNotes = request.firstLevelNotes,
            status = DocumentStatus.PENDING_SECOND_LEVEL  // skipping first-level in this system
        )

        val saved = documentRepository.save(document)
        addAuditLog(saved, DocumentStatus.SUBMITTED, DocumentStatus.PENDING_SECOND_LEVEL, "system", "Document submitted for second-level review")
        return DocumentResponse.from(saved)
    }

    // ─── Queue / Listing ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun getPendingQueue(pageable: Pageable): Page<DocumentSummary> =
        documentRepository
            .findByStatus(DocumentStatus.PENDING_SECOND_LEVEL, pageable)
            .map { DocumentSummary.from(it) }

    @Transactional(readOnly = true)
    fun getMyAssignedDocuments(pageable: Pageable): Page<DocumentSummary> {
        val reviewer = getCurrentUser()
        return documentRepository
            .findByAssignedReviewerAndStatus(reviewer, DocumentStatus.UNDER_REVIEW, pageable)
            .map { DocumentSummary.from(it) }
    }

    @Transactional(readOnly = true)
    fun getDocumentById(id: Long): DocumentResponse =
        DocumentResponse.from(findDocumentOrThrow(id))

    /**
     * Loads the uploaded file for a document.
     *
     * Files are saved on disk under a UUID-based storedFileName (see FileStorageService),
     * so the lookup must use that name; originalFileName is only used for the
     * Content-Disposition header. storedFileName is not part of DocumentResponse,
     * which is why this lives in the service rather than the controller.
     */
    @Transactional(readOnly = true)
    fun loadDocumentFile(id: Long): DocumentFile {
        val doc = findDocumentOrThrow(id)
        return DocumentFile(
            resource = fileStorageService.loadAsResource(doc.storedFileName),
            originalFileName = doc.originalFileName
        )
    }

    // ─── Workflow Actions ───────────────────────────────────────────────────

    /**
     * Reviewer picks up a document from the queue.
     * Demonstrates: state guard, assignment, audit logging.
     */
    fun claimDocument(documentId: Long): DocumentResponse {
        val doc = findDocumentOrThrow(documentId)
        val reviewer = getCurrentUser()

        if (doc.status != DocumentStatus.PENDING_SECOND_LEVEL) {
            throw BadRequestException("Document is not available for claiming. Current status: ${doc.status}")
        }

        val previousStatus = doc.status
        doc.status = DocumentStatus.UNDER_REVIEW
        doc.assignedReviewer = reviewer
        doc.reviewStartedAt = Instant.now()

        documentRepository.save(doc)
        addAuditLog(doc, previousStatus, DocumentStatus.UNDER_REVIEW, reviewer.username, "Document claimed for second-level review")

        return DocumentResponse.from(doc)
    }

    /**
     * Submit the second-level review decision.
     * @PreAuthorize: only REVIEWER or SUPERVISOR roles can call this.
     */
    @PreAuthorize("hasAnyRole('REVIEWER', 'SUPERVISOR', 'ADMIN')")
    fun submitDecision(documentId: Long, request: ReviewDecisionRequest): DocumentResponse {
        val doc = findDocumentOrThrow(documentId)
        val currentUser = getCurrentUser()

        if (doc.status != DocumentStatus.UNDER_REVIEW) {
            throw BadRequestException("Document must be UNDER_REVIEW to submit a decision. Current: ${doc.status}")
        }

        // Validate required fields per decision type
        when (request.decision) {
            ReviewDecision.REJECT -> {
                if (request.rejectionReason.isNullOrBlank())
                    throw BadRequestException("Rejection reason is required when rejecting a document")
            }
            ReviewDecision.ESCALATE -> {
                if (request.escalationReason.isNullOrBlank())
                    throw BadRequestException("Escalation reason is required when escalating a document")
            }
            ReviewDecision.APPROVE -> { /* no extra fields needed */ }
        }

        val previousStatus = doc.status
        val newStatus = when (request.decision) {
            ReviewDecision.APPROVE   -> DocumentStatus.APPROVED
            ReviewDecision.REJECT    -> DocumentStatus.REJECTED
            ReviewDecision.ESCALATE  -> DocumentStatus.ESCALATED
        }

        doc.status = newStatus
        doc.secondLevelNotes = request.notes
        doc.rejectionReason = request.rejectionReason
        doc.escalationReason = request.escalationReason
        if (newStatus in listOf(DocumentStatus.APPROVED, DocumentStatus.REJECTED)) {
            doc.completedAt = Instant.now()
        }

        documentRepository.save(doc)
        addAuditLog(doc, previousStatus, newStatus, currentUser.username, request.notes)

        return DocumentResponse.from(doc)
    }

    /**
     * Supervisors can resolve escalated documents.
     * @PreAuthorize: ONLY SUPERVISOR or ADMIN can call this.
     */
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    fun resolveEscalation(documentId: Long, request: ReviewDecisionRequest): DocumentResponse {
        val doc = findDocumentOrThrow(documentId)

        if (doc.status != DocumentStatus.ESCALATED) {
            throw BadRequestException("Document must be ESCALATED to resolve. Current: ${doc.status}")
        }

        val newStatus = when (request.decision) {
            ReviewDecision.APPROVE -> DocumentStatus.APPROVED
            ReviewDecision.REJECT  -> {
                if (request.rejectionReason.isNullOrBlank())
                    throw BadRequestException("Rejection reason is required")
                DocumentStatus.REJECTED
            }
            else -> throw BadRequestException("Escalated documents can only be APPROVED or REJECTED")
        }

        val previousStatus = doc.status
        doc.status = newStatus
        doc.rejectionReason = request.rejectionReason
        doc.completedAt = Instant.now()

        documentRepository.save(doc)
        addAuditLog(doc, previousStatus, newStatus, getCurrentUser().username, request.notes)

        return DocumentResponse.from(doc)
    }

    // ─── Dashboard ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun getDashboardStats(): DashboardStats = DashboardStats(
        totalDocuments = documentRepository.count(),
        pendingSecondLevel = documentRepository.countByStatus(DocumentStatus.PENDING_SECOND_LEVEL),
        underReview = documentRepository.countByStatus(DocumentStatus.UNDER_REVIEW),
        approvedToday = documentRepository.countByStatus(DocumentStatus.APPROVED),   // TODO: filter by today
        rejectedToday = documentRepository.countByStatus(DocumentStatus.REJECTED),
        escalated = documentRepository.countByStatus(DocumentStatus.ESCALATED)
    )

    // ─── Helpers ───────────────────────────────────────────────────────────

    private fun findDocumentOrThrow(id: Long): Document =
        documentRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Document not found with id: $id") }

    private fun getCurrentUser() = run {
        val principal = SecurityContextHolder.getContext().authentication.principal as UserDetailsImpl
        userRepository.findByUsername(principal.username)
            .orElseThrow { ResourceNotFoundException("Authenticated user not found in database") }
    }

    private fun addAuditLog(
        document: Document,
        from: DocumentStatus,
        to: DocumentStatus,
        performedBy: String,
        notes: String? = null
    ) {
        val log = DocumentAuditLog(
            document = document,
            previousStatus = from,
            newStatus = to,
            performedBy = performedBy,
            notes = notes
        )
        auditLogRepository.save(log)
    }
}

/** The file behind a document, plus the name to offer the client when downloading. */
data class DocumentFile(
    val resource: Resource,
    val originalFileName: String
)
