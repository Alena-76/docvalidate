package com.bank.docvalidate.repository

import com.bank.docvalidate.entity.Document
import com.bank.docvalidate.entity.DocumentAuditLog
import com.bank.docvalidate.entity.User
import com.bank.docvalidate.enums.DocumentStatus
import com.bank.docvalidate.enums.DocumentType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.Optional

/**
 * Spring Data JPA repositories.
 *
 * KEY CONCEPT: You don't write SQL here. Spring generates queries from method names.
 * The rules:
 *   findBy<Field>           → WHERE field = ?
 *   findBy<Field>And<Field> → WHERE field1 = ? AND field2 = ?
 *   findBy<Field>In(list)   → WHERE field IN (...)
 *   findBy<Field>OrderBy<Field>Asc → with ORDER BY
 *   Page<T> + Pageable      → automatic pagination (LIMIT + OFFSET)
 *
 * For complex queries, use @Query with JPQL (or nativeQuery = true for raw SQL).
 */

@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByUsername(username: String): Optional<User>
    fun existsByUsername(username: String): Boolean
    fun existsByEmail(email: String): Boolean
}

@Repository
interface DocumentRepository : JpaRepository<Document, Long> {

    // Find by single status with pagination — used for reviewer queues
    fun findByStatus(status: DocumentStatus, pageable: Pageable): Page<Document>

    // Filter queue by status + document type
    fun findByStatusAndDocumentType(
        status: DocumentStatus,
        documentType: DocumentType,
        pageable: Pageable
    ): Page<Document>

    // Find all documents assigned to a specific reviewer
    fun findByAssignedReviewerAndStatus(
        reviewer: User,
        status: DocumentStatus,
        pageable: Pageable
    ): Page<Document>

    // Search by customer
    fun findByCustomerIdOrCustomerNameContainingIgnoreCase(
        customerId: String,
        customerName: String,
        pageable: Pageable
    ): Page<Document>

    // Custom JPQL: find documents overdue for SLA (submitted more than N hours ago and still pending)
    @Query("""
        SELECT d FROM Document d 
        WHERE d.status IN :statuses 
        AND d.submittedAt < :cutoffTime
        ORDER BY d.submittedAt ASC
    """)
    fun findOverdueDocuments(
        statuses: List<DocumentStatus>,
        cutoffTime: Instant
    ): List<Document>

    // Count by status — useful for dashboard
    fun countByStatus(status: DocumentStatus): Long
}

@Repository
interface DocumentAuditLogRepository : JpaRepository<DocumentAuditLog, Long> {
    fun findByDocumentIdOrderByPerformedAtDesc(documentId: Long): List<DocumentAuditLog>
}
