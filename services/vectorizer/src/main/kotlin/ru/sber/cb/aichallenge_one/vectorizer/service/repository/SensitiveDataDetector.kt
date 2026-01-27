package ru.sber.cb.aichallenge_one.vectorizer.service.repository

import org.slf4j.LoggerFactory
import java.io.File

/**
 * Service for detecting sensitive data in files and file names.
 * Prevents indexing of API keys, passwords, private keys, and other secrets.
 */
class SensitiveDataDetector {
    private val logger = LoggerFactory.getLogger(SensitiveDataDetector::class.java)

    companion object {
        // Sensitive file extensions
        private val SENSITIVE_FILE_EXTENSIONS = setOf(
            "pem", "key", "p12", "pfx", "jks", "keystore",
            "cer", "crt", "der", "csr"
        )

        // Sensitive filename patterns
        private val SENSITIVE_FILE_PATTERNS = listOf(
            Regex("""(?i)\.(env|secret|credential|password|auth).*$"""),
            Regex("""(?i)(id_rsa|id_dsa|id_ecdsa|id_ed25519)$"""),
            Regex("""(?i)^\.?npmrc$"""),
            Regex("""(?i)^\.?pypirc$"""),
            Regex("""(?i)credentials\..*$"""),
            Regex("""(?i)secrets?\..*$""")
        )

        // Sensitive content patterns - detect actual secrets in code
        private val SENSITIVE_CONTENT_PATTERNS = listOf(
            // API Keys
            Regex("""(?i)(api[_-]?key|apikey|api[_-]?secret)\s*[:=]\s*['""]?[\w\-]{16,}['""]?"""),

            // Generic secrets/tokens
            Regex("""(?i)(secret|token|password|passwd|pwd)\s*[:=]\s*['""]?[^\s'""]{8,}['""]?"""),

            // AWS credentials
            Regex("""AKIA[0-9A-Z]{16}"""),
            Regex("""(?i)aws[_-]?secret[_-]?access[_-]?key\s*[:=]\s*['""]?[\w/+]{40}['""]?"""),

            // Private keys (PEM format indicators)
            Regex("""-----BEGIN (RSA |DSA |EC )?PRIVATE KEY-----"""),

            // Database URLs with embedded credentials
            Regex("""(?i)jdbc:.*://[^:]+:[^@]+@"""),
            Regex("""(?i)postgres://[^:]+:[^@]+@"""),
            Regex("""(?i)mysql://[^:]+:[^@]+@"""),
            Regex("""(?i)mongodb(\+srv)?://[^:]+:[^@]+@"""),

            // GitHub tokens
            Regex("""gh[pousr]_[A-Za-z0-9_]{36,}"""),

            // Generic base64-encoded secrets (at least 40 chars, likely a secret)
            Regex("""(?<![A-Za-z0-9])[A-Za-z0-9+/]{40,}={0,2}(?![A-Za-z0-9])"""),

            // Bearer tokens
            Regex("""(?i)bearer\s+[A-Za-z0-9\-._~+/]+=*"""),

            // OpenAI API keys
            Regex("""sk-[A-Za-z0-9]{48}"""),

            // Slack tokens
            Regex("""xox[pboa]-[0-9]{10,13}-[0-9]{10,13}-[A-Za-z0-9]{24,}"""),

            // Google API keys
            Regex("""AIza[0-9A-Za-z\-_]{35}""")
        )
    }

    /**
     * Checks if a file should be considered sensitive based on its path/name.
     */
    fun isSensitiveFile(file: File): Boolean {
        file.name.lowercase()
        val extension = file.extension.lowercase()

        // Check extension
        if (extension in SENSITIVE_FILE_EXTENSIONS) {
            logger.debug("File marked as sensitive due to extension: ${file.name}")
            return true
        }

        // Check filename patterns
        for (pattern in SENSITIVE_FILE_PATTERNS) {
            if (pattern.containsMatchIn(file.name)) {
                logger.debug("File marked as sensitive due to name pattern: ${file.name}")
                return true
            }
        }

        return false
    }

    /**
     * Scans file content for sensitive data patterns.
     * Returns detection result with details about what was found.
     */
    fun containsSensitiveData(content: String, filePath: String? = null): DetectionResult {
        val matches = mutableListOf<SensitiveMatch>()

        for ((index, pattern) in SENSITIVE_CONTENT_PATTERNS.withIndex()) {
            val found = pattern.findAll(content)
            found.forEach { matchResult ->
                val match = SensitiveMatch(
                    pattern = "PATTERN_$index",
                    matchedText = matchResult.value.take(50), // Truncate for logging
                    position = matchResult.range.first
                )
                matches.add(match)
            }
        }

        if (matches.isNotEmpty()) {
            logger.warn(
                "Detected ${matches.size} potential secret(s) in ${filePath ?: "content"}: " +
                        matches.joinToString(", ") { it.pattern }
            )
        }

        return DetectionResult(
            detected = matches.isNotEmpty(),
            matches = matches
        )
    }

    /**
     * Quick check if content likely contains sensitive data.
     * More performant than full scan.
     */
    fun quickContainsSensitiveData(content: String): Boolean {
        // Quick heuristics
        return content.contains("-----BEGIN", ignoreCase = true) ||
                content.contains("AKIA", ignoreCase = false) ||
                Regex("""(?i)(api[_-]?key|secret|password)\s*[:=]""").containsMatchIn(content)
    }
}

/**
 * Result of sensitive data detection.
 */
data class DetectionResult(
    val detected: Boolean,
    val matches: List<SensitiveMatch>
)

/**
 * Information about a detected sensitive pattern match.
 */
data class SensitiveMatch(
    val pattern: String,
    val matchedText: String,
    val position: Int
)
