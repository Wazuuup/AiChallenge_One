package ru.sber.cb.aichallenge_one.vectorizer.service.repository

import org.junit.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SensitiveDataDetectorTest {
    private val detector = SensitiveDataDetector()

    @Test
    fun `should detect API keys in code`() {
        val content = """
            val apiKey = "sk-abc123def456ghi789jkl012mno345pqr678stu901vwx234"
            client.authenticate(apiKey)
        """.trimIndent()

        val result = detector.containsSensitiveData(content)

        assertTrue(result.detected, "Should detect API key")
        assertTrue(result.matches.isNotEmpty(), "Should have matches")
    }

    @Test
    fun `should detect AWS credentials`() {
        val content = "AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE"

        val result = detector.containsSensitiveData(content)
        assertTrue(result.detected, "Should detect AWS credentials")
    }

    @Test
    fun `should detect database URLs with credentials`() {
        val content = "jdbc:postgresql://localhost:5432/db?user=admin&password=secret123"

        val result = detector.containsSensitiveData(content)
        assertTrue(result.detected, "Should detect database credentials")
    }

    @Test
    fun `should detect private key markers`() {
        val content = """
            -----BEGIN RSA PRIVATE KEY-----
            MIIEpAIBAAKCAQEA0Z3VS5JJcds...
            -----END RSA PRIVATE KEY-----
        """.trimIndent()

        val result = detector.containsSensitiveData(content)
        assertTrue(result.detected, "Should detect private key")
    }

    @Test
    fun `should not flag safe environment variable names`() {
        val content = """
            val API_ENDPOINT = "https://api.example.com"
            val BASE_URL = "https://example.com"
        """.trimIndent()

        val result = detector.containsSensitiveData(content)
        assertFalse(result.detected, "Should not detect safe API endpoint definitions")
    }

    @Test
    fun `should detect sensitive file by extension`() {
        assertTrue(detector.isSensitiveFile(File("private_key.pem")))
        assertTrue(detector.isSensitiveFile(File("keystore.jks")))
        assertTrue(detector.isSensitiveFile(File("cert.p12")))
    }

    @Test
    fun `should detect sensitive file by name pattern`() {
        assertTrue(detector.isSensitiveFile(File(".env.local")))
        assertTrue(detector.isSensitiveFile(File(".env.production")))
        assertTrue(detector.isSensitiveFile(File("credentials.json")))
        assertTrue(detector.isSensitiveFile(File("secrets.yaml")))
    }

    @Test
    fun `should not mark normal files as sensitive`() {
        assertFalse(detector.isSensitiveFile(File("README.md")))
        assertFalse(detector.isSensitiveFile(File("config.yaml")))
        assertFalse(detector.isSensitiveFile(File("Application.kt")))
    }

    @Test
    fun `quick check should work for common patterns`() {
        assertTrue(detector.quickContainsSensitiveData("-----BEGIN PRIVATE KEY-----"))
        assertTrue(detector.quickContainsSensitiveData("AKIAIOSFODNN7EXAMPLE"))
        assertTrue(detector.quickContainsSensitiveData("api_key = secret123"))

        assertFalse(detector.quickContainsSensitiveData("normal code content"))
    }
}
