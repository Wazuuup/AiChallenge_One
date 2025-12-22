package ru.sber.cb.aichallenge_one.vectorizer.service

import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.vectorizer.repository.EmbeddingRepository

data class VectorizationResult(
    val success: Boolean,
    val filesProcessed: Int,
    val chunksCreated: Int,
    val filesSkipped: List<String>,
    val errors: List<String>
)

class VectorizerService(
    private val fileProcessingService: FileProcessingService,
    private val chunkingService: ChunkingService,
    private val ollamaClient: OllamaEmbeddingClient,
    private val embeddingRepository: EmbeddingRepository
) {
    private val logger = LoggerFactory.getLogger(VectorizerService::class.java)

    suspend fun vectorizeFolder(
        folderPath: String,
        model: String = "nomic-embed-text"
    ): VectorizationResult = coroutineScope {
        val errors = mutableListOf<String>()
        val skippedFiles = mutableListOf<String>()
        var totalChunks = 0
        var processedFiles = 0

        // Step 1: Process folder to get all text files
        val fileContents: List<FileProcessingService.FileContent> = fileProcessingService.processFolder(folderPath)

        if (fileContents.isEmpty()) {
            return@coroutineScope VectorizationResult(
                success = false,
                filesProcessed = 0,
                chunksCreated = 0,
                filesSkipped = emptyList(),
                errors = listOf("No text files found in folder: $folderPath")
            )
        }

        logger.info("Processing ${fileContents.size} files from $folderPath")

        // Step 2: Process each file
        for (fileContent in fileContents) {
            try {
                // Delete existing embeddings for this file
                val deleted = embeddingRepository.deleteByFilePath(fileContent.path)
                if (deleted > 0) {
                    logger.info("Deleted $deleted existing embeddings for ${fileContent.name}")
                }

                // Chunk the file
                val chunks = chunkingService.chunkText(fileContent.content, fileContent.path)

                if (chunks.isEmpty()) {
                    logger.warn("No chunks created for file: ${fileContent.path}")
                    skippedFiles.add(fileContent.path)
                    continue
                }

                logger.info("Created ${chunks.size} chunks for ${fileContent.name}")

                // Generate embeddings and store
                var successfulChunks = 0
                for (chunk in chunks) {
                    val embedding = ollamaClient.generateEmbedding(chunk.text, model)

                    if (embedding == null) {
                        errors.add("Failed to generate embedding for ${fileContent.name}:${chunk.chunkIndex}")
                        continue
                    }

                    val inserted = embeddingRepository.insertEmbedding(
                        filePath = fileContent.path,
                        fileName = fileContent.name,
                        chunkIndex = chunk.chunkIndex,
                        chunkText = chunk.text,
                        tokenCount = chunk.tokenCount,
                        embedding = embedding
                    )

                    if (inserted) {
                        successfulChunks++
                    } else {
                        errors.add("Failed to store embedding for ${fileContent.name}:${chunk.chunkIndex}")
                    }
                }

                if (successfulChunks > 0) {
                    processedFiles++
                    totalChunks += successfulChunks
                    logger.info("Successfully processed ${fileContent.name}: $successfulChunks chunks")
                } else {
                    skippedFiles.add(fileContent.path)
                }

            } catch (e: Exception) {
                logger.error("Error processing file ${fileContent.path}", e)
                errors.add("Error processing ${fileContent.name}: ${e.message}")
                skippedFiles.add(fileContent.path)
            }
        }

        VectorizationResult(
            success = errors.isEmpty(),
            filesProcessed = processedFiles,
            chunksCreated = totalChunks,
            filesSkipped = skippedFiles,
            errors = errors
        )
    }
}
