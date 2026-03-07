package com.bank.docvalidate.entity

import com.bank.docvalidate.enums.DocumentStatus
import com.bank.docvalidate.enums.DocumentType
import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate

/**
 * The core domain object. Represents a customer document submitted for second-level review.
 *
 * Spring Boot / JPA concepts illustrated:
 *  - @ManyToOne: many documents can be assigned to one reviewer (nullable = unassigned)
 *  - @OneToMany(mappedBy): the "inverse" side of the relationship; documents own their audit logs
 *  - @Enumerated(EnumType.STRING): persist enum name as string, not ordinal
 *  - Cascade / orphanRemoval: deleting a document removes its audit logs too
 */
@Entity
@Table(name = "documents")
data class Document(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    // ─── Customer & Document Info ─────────────────────────────────
    @Column(nullable = false, length = 50)
    val customerId: String,

    @Column(nullable = false, length = 100)
    val customerName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    val documentType: DocumentType,

    val documentExpiryDate: LocalDate? = null,

    // ─── File Storage ─────────────────────────────────────────────
    @Column(nullable = false)
    val originalFileName: String,       // e.g. "passport_scan.pdf"

    @Column(nullable = false)
    val storedFileName: String,         // UUID-based name on disk

    @Column(nullable = false, length = 20)
    val fileExtension: String,

    val fileSizeBytes: Long,

    // ─── Workflow State ───────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: DocumentStatus = DocumentStatus.SUBMITTED,

    // ─── Review Notes ─────────────────────────────────────────────
    @Column(columnDefinition = "TEXT")
    var firstLevelNotes: String? = null,

    @Column(columnDefinition = "TEXT")
    var secondLevelNotes: String? = null,

    @Column(columnDefinition = "TEXT")
    var rejectionReason: String? = null,

    @Column(columnDefinition = "TEXT")
    var escalationReason: String? = null,

    // ─── Assignments ──────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_reviewer_id")
    var assignedReviewer: User? = null,

    // ─── Timestamps ───────────────────────────────────────────────
    val submittedAt: Instant = Instant.now(),
    var reviewStartedAt: Instant? = null,
    var completedAt: Instant? = null,

    // ─── Audit Trail ──────────────────────────────────────────────
    @OneToMany(mappedBy = "document", cascade = [CascadeType.ALL], orphanRemoval = true)
    val auditLogs: MutableList<DocumentAuditLog> = mutableListOf()
)
