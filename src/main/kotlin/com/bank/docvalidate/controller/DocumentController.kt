package com.bank.docvalidate.controller

import com.bank.docvalidate.dto.*
import com.bank.docvalidate.service.DocumentService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

/**
 * Document review API endpoints.
 *
 * KEY CONCEPTS:
 *  @RequestParam: query string params (?page=0&size=20)
 *  @PathVariable: URL path segments (/documents/{id})
 *  @RequestPart: for multipart requests mixing JSON + file upload
 *  Pageable: Spring Data's pagination abstraction (page, size, sort)
 *  ResponseEntity<T>: gives full control over status code, headers, and body
 *
 * @SecurityRequirement: tells Swagger UI that these endpoints need a Bearer token.
 */
@RestController
@RequestMapping("/api/documents")
@Tag(name = "Documents", description = "Document submission and review workflow")
@SecurityRequirement(name = "bearerAuth")
class DocumentController(
    private val documentService: DocumentService
) {

    // ─── Submit ────────────────────────────────────────────────────────────

    /**
     * Multipart endpoint: JSON metadata + file in one request.
     * curl -F "request={...};type=application/json" -F "file=@passport.pdf" /api/documents
     */
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit a document for second-level review (multipart: JSON + file)")
    fun submitDocument(
        @RequestPart("request") @Valid request: DocumentSubmitRequest,
        @RequestPart("file") file: MultipartFile
    ): ApiResponse<DocumentResponse> {
        val doc = documentService.submitDocument(request, file)
        return ApiResponse(data = doc, message = "Document submitted successfully")
    }

    // ─── Retrieve ──────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @Operation(summary = "Get full document details including audit trail")
    fun getDocument(@PathVariable id: Long): ApiResponse<DocumentResponse> {
        return ApiResponse(data = documentService.getDocumentById(id))
    }

    @GetMapping("/{id}/file")
    @Operation(summary = "Download the actual document file")
    fun downloadFile(@PathVariable id: Long): ResponseEntity<org.springframework.core.io.Resource> {
        val file = documentService.loadDocumentFile(id)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${file.originalFileName}\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(file.resource)
    }

    // ─── Queue & Lists ──────────────────────────────────────────────────────

    @GetMapping("/queue")
    @Operation(summary = "Get pending documents queue (paginated, sorted by submission date)")
    fun getPendingQueue(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ApiResponse<Page<DocumentSummary>> {
        val pageable = PageRequest.of(page, size, Sort.by("submittedAt").ascending())
        return ApiResponse(data = documentService.getPendingQueue(pageable))
    }

    @GetMapping("/my-assignments")
    @Operation(summary = "Get documents currently assigned to the authenticated reviewer")
    fun getMyAssignments(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ApiResponse<Page<DocumentSummary>> {
        val pageable = PageRequest.of(page, size, Sort.by("reviewStartedAt").ascending())
        return ApiResponse(data = documentService.getMyAssignedDocuments(pageable))
    }

    // ─── Workflow Actions ───────────────────────────────────────────────────

    @PostMapping("/{id}/claim")
    @Operation(summary = "Claim a document from the queue to start reviewing it")
    fun claimDocument(@PathVariable id: Long): ApiResponse<DocumentResponse> {
        return ApiResponse(data = documentService.claimDocument(id), message = "Document claimed")
    }

    @PostMapping("/{id}/decision")
    @Operation(summary = "Submit review decision: APPROVE, REJECT, or ESCALATE")
    fun submitDecision(
        @PathVariable id: Long,
        @Valid @RequestBody request: ReviewDecisionRequest
    ): ApiResponse<DocumentResponse> {
        return ApiResponse(data = documentService.submitDecision(id, request), message = "Decision recorded")
    }

    @PostMapping("/{id}/resolve-escalation")
    @Operation(summary = "Supervisor resolves an escalated document (APPROVE or REJECT)")
    fun resolveEscalation(
        @PathVariable id: Long,
        @Valid @RequestBody request: ReviewDecisionRequest
    ): ApiResponse<DocumentResponse> {
        return ApiResponse(data = documentService.resolveEscalation(id, request), message = "Escalation resolved")
    }

    // ─── Dashboard ─────────────────────────────────────────────────────────

    @GetMapping("/stats")
    @Operation(summary = "Get dashboard statistics (counts per status)")
    fun getDashboardStats(): ApiResponse<DashboardStats> {
        return ApiResponse(data = documentService.getDashboardStats())
    }
}
