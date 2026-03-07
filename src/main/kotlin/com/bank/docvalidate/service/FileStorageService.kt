package com.bank.docvalidate.service

import com.bank.docvalidate.enums.DocumentType
import com.bank.docvalidate.exception.BadRequestException
import com.bank.docvalidate.exception.ResourceNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.core.io.UrlResource
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Handles file upload and retrieval.
 *
 * KEY CONCEPTS:
 *  - @Value: injects configuration from application.properties
 *  - Path / Files (Java NIO): modern file handling API
 *  - MultipartFile: Spring's abstraction for uploaded files
 *  - Resource: Spring's abstraction for file download streaming
 *
 *  In production you'd swap local storage for S3 / Azure Blob Storage.
 *  The service interface would stay the same — that's the power of abstraction!
 */
@Service
class FileStorageService(
    @Value("\${app.upload.dir}") private val uploadDir: String
) {
    private val log = LoggerFactory.getLogger(FileStorageService::class.java)
    private val uploadPath: Path by lazy {
        Paths.get(uploadDir).toAbsolutePath().normalize().also { Files.createDirectories(it) }
    }

    data class StoredFile(
        val storedFileName: String,
        val originalFileName: String,
        val extension: String,
        val sizeBytes: Long
    )

    fun store(file: MultipartFile, documentType: DocumentType): StoredFile {
        if (file.isEmpty) throw BadRequestException("Uploaded file is empty")

        val originalName = file.originalFilename ?: throw BadRequestException("File name is missing")
        val extension = originalName.substringAfterLast('.', "").lowercase()

        // Validate extension against allowed types for this document type
        if (extension !in documentType.allowedExtensions) {
            throw BadRequestException(
                "File type '.$extension' is not allowed for ${documentType.displayName}. " +
                "Allowed: ${documentType.allowedExtensions.joinToString { ".$it" }}"
            )
        }

        // Max 10MB enforced at config level, but double-check here
        if (file.size > 10 * 1024 * 1024) {
            throw BadRequestException("File size exceeds 10MB limit")
        }

        // Generate unique filename to avoid collisions
        val storedName = "${UUID.randomUUID()}.$extension"
        val targetPath = uploadPath.resolve(storedName)

        Files.copy(file.inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING)
        log.info("Stored file: $storedName (original: $originalName)")

        return StoredFile(
            storedFileName = storedName,
            originalFileName = originalName,
            extension = extension,
            sizeBytes = file.size
        )
    }

    fun loadAsResource(storedFileName: String): Resource {
        val filePath = uploadPath.resolve(storedFileName).normalize()
        val resource = UrlResource(filePath.toUri())

        if (!resource.exists()) {
            throw ResourceNotFoundException("File not found: $storedFileName")
        }
        return resource
    }

    fun delete(storedFileName: String) {
        val filePath = uploadPath.resolve(storedFileName).normalize()
        Files.deleteIfExists(filePath)
    }
}
