package com.bank.docvalidate.entity

import com.bank.docvalidate.enums.DocumentStatus
import jakarta.persistence.*
import java.time.Instant

/**
 * Immutable record of every action taken on a document.
 * This gives full traceability — critical in banking.
 *
 * Key concept: this is the "many" side of @ManyToOne with Document.
 * We NEVER delete audit logs; they are append-only.
 */
@Entity
@Table(name = "document_audit_logs")
data class DocumentAuditLog(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    val document: Document,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val previousStatus: DocumentStatus,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val newStatus: DocumentStatus,

    @Column(nullable = false, length = 100)
    val performedBy: String,    // username of the reviewer who made this action

    @Column(columnDefinition = "TEXT")
    val notes: String? = null,

    val performedAt: Instant = Instant.now()
)
