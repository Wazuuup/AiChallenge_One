package ru.sber.cb.aichallenge_one.vectorizer.service.repository

import org.slf4j.LoggerFactory
import java.io.File

/**
 * Validates repository paths and enforces security constraints.
 * Prevents path traversal attacks and unauthorized directory access.
 */
class RepositoryValidator(private val config: RepositoryLimits) {
    private val logger = LoggerFactory.getLogger(RepositoryValidator::class.java)

    /**
     * Validates a repository path against security constraints.
     */
    fun validateRepository(path: String): ValidationResult {
        // 1. Check for path traversal attempts
        if (path.contains("..")) {
            logger.warn("Path traversal attempt detected: $path")
            return ValidationResult.failure(
                errorCode = "PATH_TRAVERSAL",
                message = "Path traversal patterns (..) are not allowed"
            )
        }

        // 2. Normalize and canonicalize path
        val file = try {
            File(path).canonicalFile
        } catch (e: Exception) {
            logger.warn("Invalid path: $path", e)
            return ValidationResult.failure(
                errorCode = "INVALID_PATH",
                message = "Invalid file path: ${e.message}"
            )
        }

        // 3. Check if path exists
        if (!file.exists()) {
            return ValidationResult.failure(
                errorCode = "PATH_NOT_FOUND",
                message = "Path does not exist: $path"
            )
        }

        // 4. Check if it's a directory
        if (!file.isDirectory) {
            return ValidationResult.failure(
                errorCode = "NOT_A_DIRECTORY",
                message = "Path is not a directory: $path"
            )
        }

        // 5. Check if it's a Git repository
        val gitDir = File(file, ".git")
        if (!gitDir.exists() || !gitDir.isDirectory) {
            return ValidationResult.failure(
                errorCode = "NOT_A_GIT_REPO",
                message = "Path is not a Git repository (no .git directory found)"
            )
        }

        // 6. Check against allowed base paths (if configured)
        if (config.allowedBasePaths.isNotEmpty()) {
            val isAllowed = config.allowedBasePaths.any { allowedBase ->
                val normalizedAllowed = File(allowedBase).canonicalFile
                file.startsWith(normalizedAllowed)
            }

            if (!isAllowed) {
                logger.warn("Access denied to path outside allowed directories: $path")
                return ValidationResult.failure(
                    errorCode = "PATH_NOT_ALLOWED",
                    message = "Path is outside allowed directories"
                )
            }
        }

        logger.info("Repository path validated successfully: $path")
        return ValidationResult.success(file)
    }

    /**
     * Estimates repository size and file count (for limit checking).
     */
    fun estimateRepositorySize(repoPath: File): RepositoryStats {
        var fileCount = 0
        var totalSize = 0L
        var maxDepth = 0

        fun walk(dir: File, depth: Int = 0) {
            if (depth > config.maxDepth) {
                maxDepth = maxOf(maxDepth, depth)
                return
            }

            // Skip .git directory for size calculation
            if (dir.name == ".git") return

            dir.listFiles()?.forEach { file ->
                when {
                    file.isDirectory -> walk(file, depth + 1)
                    file.isFile -> {
                        fileCount++
                        totalSize += file.length()

                        // Stop early if limits exceeded
                        if (fileCount > config.maxFiles) {
                            throw LimitExceededException("File count limit exceeded")
                        }
                        if (totalSize > config.maxTotalSize) {
                            throw LimitExceededException("Total size limit exceeded")
                        }
                    }
                }
            }
        }

        try {
            walk(repoPath)
        } catch (e: LimitExceededException) {
            // Caught in validation
        }

        return RepositoryStats(
            fileCount = fileCount,
            totalSizeBytes = totalSize,
            maxDepth = maxDepth
        )
    }
}

/**
 * Configuration for repository limits and security constraints.
 */
data class RepositoryLimits(
    val maxFiles: Int = 10_000,
    val maxFileSize: Long = 5 * 1024 * 1024, // 5MB
    val maxTotalSize: Long = 500 * 1024 * 1024L, // 500MB
    val maxDepth: Int = 50,
    val allowedBasePaths: List<String> = emptyList()
)

/**
 * Result of repository validation.
 */
sealed class ValidationResult {
    data class Success(val validatedPath: File) : ValidationResult()
    data class Failure(val errorCode: String, val message: String) : ValidationResult()

    val isValid: Boolean get() = this is Success
    val error: String? get() = (this as? Failure)?.message

    companion object {
        fun success(file: File) = Success(file)
        fun failure(errorCode: String, message: String) = Failure(errorCode, message)
    }
}

/**
 * Statistics about a repository.
 */
data class RepositoryStats(
    val fileCount: Int,
    val totalSizeBytes: Long,
    val maxDepth: Int
)

/**
 * Exception thrown when repository limits are exceeded during estimation.
 */
class LimitExceededException(message: String) : Exception(message)
