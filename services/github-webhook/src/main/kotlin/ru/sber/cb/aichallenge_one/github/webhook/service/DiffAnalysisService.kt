package ru.sber.cb.aichallenge_one.github.webhook.service

import org.slf4j.LoggerFactory

class DiffAnalysisService(
    private val maxDiffLines: Int
) {
    private val logger = LoggerFactory.getLogger(DiffAnalysisService::class.java)

    /**
     * Count the number of lines in a diff
     */
    fun countDiffLines(diff: String): Int {
        return diff.lines().size
    }

    /**
     * Filter out binary files from the diff
     * Returns the filtered diff and list of excluded binary file names
     */
    fun filterBinaryFiles(diff: String): FilteredDiffResult {
        val lines = diff.lines()
        val binaryFiles = mutableListOf<String>()
        val filteredLines = mutableListOf<String>()
        var skipUntilNextFile = false
        var currentFile: String? = null

        for (line in lines) {
            // Check for diff header
            if (line.startsWith("diff --git")) {
                skipUntilNextFile = false
                currentFile = line.substringAfter("a/").substringBefore(" b/")
            }

            // Check for binary file indicator
            if (line.startsWith("Binary files") || line.contains("GIT binary patch")) {
                if (currentFile != null) {
                    binaryFiles.add(currentFile)
                }
                skipUntilNextFile = true
                continue
            }

            // Add line if not skipping
            if (!skipUntilNextFile) {
                filteredLines.add(line)
            }
        }

        val filteredDiff = filteredLines.joinToString("\n")
        logger.info("Filtered binary files: ${binaryFiles.size} files excluded")

        return FilteredDiffResult(
            diff = filteredDiff,
            binaryFiles = binaryFiles
        )
    }

    /**
     * Extract keywords from diff (file paths + class/function names)
     */
    fun extractKeywords(diff: String): List<String> {
        val keywords = mutableSetOf<String>()

        // 1. Extract file paths from diff headers
        // Pattern: "diff --git a/path/to/file.kt b/path/to/file.kt"
        val filePathRegex = """diff --git a/(.*?) b/""".toRegex()
        filePathRegex.findAll(diff).forEach { match ->
            val filePath = match.groupValues[1]
            keywords.add(filePath)

            // Also add just the file name without path
            val fileName = filePath.substringAfterLast('/')
            keywords.add(fileName)
        }

        // 2. Extract class names (Kotlin/Java style)
        val classRegex = """(?:class|interface|object|enum class)\s+(\w+)""".toRegex()
        classRegex.findAll(diff).forEach { match ->
            keywords.add(match.groupValues[1])
        }

        // 3. Extract function names (Kotlin/Java style)
        val funRegex = """fun\s+(\w+)""".toRegex()
        funRegex.findAll(diff).forEach { match ->
            keywords.add(match.groupValues[1])
        }

        // 4. Extract package names
        val packageRegex = """package\s+([\w.]+)""".toRegex()
        packageRegex.findAll(diff).forEach { match ->
            keywords.add(match.groupValues[1])
        }

        val result = keywords.toList()
        logger.info("Extracted ${result.size} keywords from diff")
        logger.debug("Keywords: ${result.take(10)}")

        return result
    }

    /**
     * Validate diff size
     */
    fun validateDiffSize(diff: String): ValidationResult {
        val lineCount = countDiffLines(diff)

        return if (lineCount > maxDiffLines) {
            logger.warn("Diff too large: $lineCount lines (max: $maxDiffLines)")
            ValidationResult.TooLarge(lineCount, maxDiffLines)
        } else {
            logger.info("Diff size OK: $lineCount lines")
            ValidationResult.Valid
        }
    }

    /**
     * Check if diff contains only deletions
     */
    fun isOnlyDeletions(diff: String): Boolean {
        val lines = diff.lines()
        var hasAdditions = false

        for (line in lines) {
            // Skip metadata lines
            if (line.startsWith("diff --git") ||
                line.startsWith("index") ||
                line.startsWith("---") ||
                line.startsWith("+++") ||
                line.startsWith("@@")
            ) {
                continue
            }

            // Check for additions (lines starting with +)
            if (line.startsWith("+") && !line.startsWith("+++")) {
                hasAdditions = true
                break
            }
        }

        return !hasAdditions
    }

    data class FilteredDiffResult(
        val diff: String,
        val binaryFiles: List<String>
    )

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class TooLarge(val actualLines: Int, val maxLines: Int) : ValidationResult()
    }
}
