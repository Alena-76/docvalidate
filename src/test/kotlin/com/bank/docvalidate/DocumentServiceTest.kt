package com.bank.docvalidate

import com.bank.docvalidate.dto.ReviewDecision
import com.bank.docvalidate.dto.ReviewDecisionRequest
import com.bank.docvalidate.entity.Document
import com.bank.docvalidate.entity.DocumentAuditLog
import com.bank.docvalidate.entity.User
import com.bank.docvalidate.enums.DocumentStatus
import com.bank.docvalidate.enums.DocumentType
import com.bank.docvalidate.enums.UserRole
import com.bank.docvalidate.exception.BadRequestException
import com.bank.docvalidate.repository.DocumentAuditLogRepository
import com.bank.docvalidate.repository.DocumentRepository
import com.bank.docvalidate.repository.UserRepository
import com.bank.docvalidate.security.UserDetailsImpl
import com.bank.docvalidate.service.DocumentService
import com.bank.docvalidate.service.FileStorageService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.core.io.Resource
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Optional

/**
 * Unit tests for DocumentService.
 *
 * KEY TESTING CONCEPTS:
 *  - Unit tests: test a single class in isolation, mocking all dependencies
 *  - Mockito: creates fake versions of dependencies (mock())
 *    - whenever().thenReturn(): define what a mock returns when called
 *    - verify(): assert that a mock method was called
 *  - @BeforeEach: runs before each test to set up shared state
 *  - assertThrows: verifies an exception is thrown
 *
 * These tests don't need a running database or Spring context — they're fast!
 * For integration tests (testing the full stack), use @SpringBootTest.
 */
class DocumentServiceTest {

    // Mocks — fake implementations of our repositories
    private val documentRepository: DocumentRepository = mock()
    private val auditLogRepository: DocumentAuditLogRepository = mock()
    private val userRepository: UserRepository = mock()
    private val fileStorageService: FileStorageService = mock()

    // The real class we're testing
    private lateinit var documentService: DocumentService

    private val testReviewer = User(
        id = 1L,
        username = "reviewer1",
        password = "hashed",
        fullName = "Test Reviewer",
        email = "reviewer@bank.com",
        roles = setOf(UserRole.ROLE_REVIEWER)
    )

    @BeforeEach
    fun setUp() {
        documentService = DocumentService(
            documentRepository, auditLogRepository, userRepository, fileStorageService
        )
        // Mock the Spring Security context so service can call getCurrentUser()
        mockSecurityContext("reviewer1")
        whenever(userRepository.findByUsername("reviewer1")).thenReturn(Optional.of(testReviewer))
    }

    @Test
    fun `claimDocument should set status to UNDER_REVIEW and assign reviewer`() {
        // ARRANGE: set up the document in the correct state
        val doc = buildDocument(DocumentStatus.PENDING_SECOND_LEVEL)
        whenever(documentRepository.findById(1L)).thenReturn(Optional.of(doc))
        doAnswer { it.arguments[0] }.whenever(documentRepository).save(any<Document>())
        doAnswer { it.arguments[0] }.whenever(auditLogRepository).save(any<DocumentAuditLog>())

        // ACT
        val result = documentService.claimDocument(1L)

        // ASSERT
        assertEquals(DocumentStatus.UNDER_REVIEW, result.status)
        assertNotNull(result.assignedReviewerName)
        verify(documentRepository).save(any())        // verify DB was updated
        verify(auditLogRepository).save(any())        // verify audit trail was written
    }

    @Test
    fun `claimDocument should throw BadRequestException if document is not PENDING`() {
        val doc = buildDocument(DocumentStatus.UNDER_REVIEW)  // already claimed!
        whenever(documentRepository.findById(1L)).thenReturn(Optional.of(doc))

        assertThrows<BadRequestException> {
            documentService.claimDocument(1L)
        }
    }

    @Test
    fun `submitDecision REJECT should require a rejection reason`() {
        val doc = buildDocument(DocumentStatus.UNDER_REVIEW)
        doc.assignedReviewer = testReviewer
        whenever(documentRepository.findById(1L)).thenReturn(Optional.of(doc))

        // No rejectionReason provided — should fail validation
        val request = ReviewDecisionRequest(
            decision = ReviewDecision.REJECT,
            rejectionReason = null  // missing!
        )

        assertThrows<BadRequestException> {
            documentService.submitDecision(1L, request)
        }
    }

    @Test
    fun `submitDecision APPROVE should complete the document`() {
        val doc = buildDocument(DocumentStatus.UNDER_REVIEW)
        doc.assignedReviewer = testReviewer
        whenever(documentRepository.findById(1L)).thenReturn(Optional.of(doc))
        doAnswer { it.arguments[0] }.whenever(documentRepository).save(any<Document>())
        doAnswer { it.arguments[0] }.whenever(auditLogRepository).save(any<DocumentAuditLog>())

        val request = ReviewDecisionRequest(decision = ReviewDecision.APPROVE, notes = "Looks good")
        val result = documentService.submitDecision(1L, request)

        assertEquals(DocumentStatus.APPROVED, result.status)
        assertNotNull(result.completedAt)
    }

    @Test
    fun `loadDocumentFile should look up the file by its stored name`() {
        val doc = buildDocument(DocumentStatus.APPROVED)
        val resource: Resource = mock()
        whenever(documentRepository.findById(1L)).thenReturn(Optional.of(doc))
        whenever(fileStorageService.loadAsResource("uuid-id.pdf")).thenReturn(resource)

        val result = documentService.loadDocumentFile(1L)

        assertSame(resource, result.resource)
        assertEquals("id.pdf", result.originalFileName)  // still offered to the client under its original name
        verify(fileStorageService).loadAsResource("uuid-id.pdf")
        verify(fileStorageService, never()).loadAsResource("id.pdf")
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private fun buildDocument(status: DocumentStatus) = Document(
        id = 1L,
        customerId = "CUST001",
        customerName = "John Doe",
        documentType = DocumentType.NATIONAL_ID,
        originalFileName = "id.pdf",
        storedFileName = "uuid-id.pdf",
        fileExtension = "pdf",
        fileSizeBytes = 1024L,
        status = status
    )

    private fun mockSecurityContext(username: String) {
        val user = User(id = 1L, username = username, password = "", fullName = "Test", email = "t@t.com")
        val userDetails = UserDetailsImpl(user)
        val auth: Authentication = mock {
            on { principal } doReturn userDetails
            on { isAuthenticated } doReturn true
        }
        val securityContext: SecurityContext = mock {
            on { authentication } doReturn auth
        }
        SecurityContextHolder.setContext(securityContext)
    }
}
