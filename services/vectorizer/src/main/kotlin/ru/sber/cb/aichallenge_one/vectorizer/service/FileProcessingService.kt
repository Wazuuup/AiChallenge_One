package ru.sber.cb.aichallenge_one.vectorizer.service

import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.StandardCharsets

class FileProcessingService {
    private val logger = LoggerFactory.getLogger(FileProcessingService::class.java)

    companion object {
        val TEXT_FILE_EXTENSIONS = setOf(
            "txt", "md", "kt", "java", "scala", "py", "js", "ts",
            "json", "xml", "yaml", "yml", "properties", "conf",
            "gradle", "kts", "html", "css", "sql", "sh", "bat",
            "c", "cpp", "h", "hpp", "go", "rs", "rb", "php",
            "swift", "m", "mm", "cs", "vb", "r", "jl"
        )
    }

    data class FileContent(
        val path: String,
        val name: String,
        val content: String,
        val extension: String
    )

    fun processFolder(folderPath: String): List<FileContent> {
        val folder = File(folderPath)

        if (!folder.exists()) {
            logger.error("Folder does not exist: $folderPath")
            return emptyList()
        }

        if (!folder.isDirectory) {
            logger.error("Path is not a directory: $folderPath")
            return emptyList()
        }

        val fileContents = mutableListOf<FileContent>()

        folder.walkTopDown()
            .filter { it.isFile }
            .filter { isTextFile(it) }
            .forEach { file ->
                try {
                    val content = file.readText(StandardCharsets.UTF_8)
                    fileContents.add(
                        FileContent(
                            path = file.absolutePath,
                            name = file.name,
                            content = content,
                            extension = file.extension
                        )
                    )
                    logger.debug("Processed file: ${file.absolutePath}")
                } catch (e: Exception) {
                    logger.error("Failed to read file: ${file.absolutePath}", e)
                }
            }

        logger.info("Processed ${fileContents.size} files from $folderPath")
        return fileContents
    }

    private fun isTextFile(file: File): Boolean {
        return file.extension.lowercase() in TEXT_FILE_EXTENSIONS
    }
}
