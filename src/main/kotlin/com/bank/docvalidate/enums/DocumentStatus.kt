package com.bank.docvalidate.enums

/**
 * Represents the lifecycle of a document going through second-level review.
 *
 * State transitions:
 *   SUBMITTED → PENDING_FIRST_LEVEL (handled by another system)
 *   PENDING_SECOND_LEVEL → UNDER_REVIEW → APPROVED
 *                                       → REJECTED
 *                                       → ESCALATED → APPROVED / REJECTED
 */
enum class DocumentStatus {
    SUBMITTED,              // Initial state when document arrives
    PENDING_SECOND_LEVEL,   // Passed first review, awaiting second-level reviewer
    UNDER_REVIEW,           // Actively being reviewed by second-level reviewer
    APPROVED,               // Document accepted
    REJECTED,               // Document rejected (rejectionReason required)
    ESCALATED,              // Needs supervisor attention
    CANCELLED               // Customer withdrew or document expired
}
