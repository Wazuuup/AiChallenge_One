package ru.sber.cb.aichallenge_one.vectorizer.service.repository

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepositoryValidatorTest {

    @Test
    fun `should reject path traversal attempts`() {
        val limits = RepositoryLimits()
        val validator = RepositoryValidator(limits)

        val result = validator.validateRepository("../../etc/passwd")

        assertFalse(result.isValid, "Should reject path traversal")
        assertTrue(result.error?.contains("traversal") == true, "Should mention path traversal")
    }

    @Test
    fun `should reject non-existent paths`() {
        val limits = RepositoryLimits()
        val validator = RepositoryValidator(limits)

        val result = validator.validateRepository("/nonexistent/path/to/repo")

        assertFalse(result.isValid, "Should reject non-existent path")
    }

    @Test
    fun `should reject non-directory paths`() {
        val limits = RepositoryLimits()
        val validator = RepositoryValidator(limits)

        // Create a temporary file (not a directory)
        val tempFile = kotlin.io.path.createTempFile().toFile()
        tempFile.deleteOnExit()

        val result = validator.validateRepository(tempFile.absolutePath)

        assertFalse(result.isValid, "Should reject file path (not a directory)")
    }

    @Test
    fun `should reject non-git directories`() {
        val limits = RepositoryLimits()
        val validator = RepositoryValidator(limits)

        // Create a temporary directory without .git
        val tempDir = kotlin.io.path.createTempDirectory().toFile()
        tempDir.deleteOnExit()

        val result = validator.validateRepository(tempDir.absolutePath)

        assertFalse(result.isValid, "Should reject non-git directory")
        assertTrue(result.error?.contains("Git repository") == true, "Should mention Git repository")
    }

    @Test
    fun `should enforce allowed paths when configured`() {
        val limits = RepositoryLimits(
            allowedBasePaths = listOf("C:\\Users\\allowed", "/home/user/allowed")
        )
        val validator = RepositoryValidator(limits)

        val result = validator.validateRepository("C:\\Users\\forbidden\\repo")

        assertFalse(result.isValid, "Should reject path outside allowed directories")
        assertTrue(result.error?.contains("allowed") == true, "Should mention allowed directories")
    }

    @Test
    fun `should allow empty allowed paths list`() {
        val limits = RepositoryLimits(allowedBasePaths = emptyList())
        val validator = RepositoryValidator(limits)

        // When allowed paths is empty, any path should be allowed (if it exists and is a valid git repo)
        // Since we can't create a real git repo in unit test, we just verify the logic doesn't fail
        val result = validator.validateRepository("C:\\some\\path")

        // Will fail for other reasons (not exists), but not for allowed paths
        assertFalse(result.error?.contains("allowed") == true, "Should not mention allowed directories")
    }
}
