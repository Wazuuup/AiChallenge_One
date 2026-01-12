package ru.sber.cb.aichallenge_one.vectorizer.service.repository

import kotlinx.coroutines.coroutineScope
import org.eclipse.jgit.api.Git
import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.models.vectorizer.*
import ru.sber.cb.aichallenge_one.vectorizer.repository.EmbeddingRepository
import ru.sber.cb.aichallenge_one.vectorizer.service.ChunkingService
import ru.sber.cb.aichallenge_one.vectorizer.service.FileProcessingService
import ru.sber.cb.aichallenge_one.vectorizer.service.OllamaEmbeddingClient
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Service for vectorizing Git repositories with .gitignore support and security scanning.
 */
class RepositoryVectorizationService(
    private val gitIgnoreService: GitIgnoreService,
    private val sensitiveDataDetector: SensitiveDataDetector,
    private val repositoryValidator: RepositoryValidator,
    private val chunkingService: ChunkingService,
    private val ollamaClient: OllamaEmbeddingClient,
    private val embeddingRepository: EmbeddingRepository,
    private val fileProcessingService: FileProcessingService
) {
    private val logger = LoggerFactory.getLogger(RepositoryVectorizationService::class.java)

    suspend fun vectorizeRepository(request: RepositoryVectorizeRequest): RepositoryVectorizeResponse =
        coroutineScope {
            val startTime = System.currentTimeMillis()

            // 1. Validate repository path
            val validation = repositoryValidator.validateRepository(request.repositoryPath)
            if (!validation.isValid) {
                logger.error("Repository validation failed: ${validation.error}")
                return@coroutineScope RepositoryVectorizeResponse(
                    success = false,
                    filesProcessed = 0,
                    chunksCreated = 0,
                    filesSkipped = emptyList(),
                    errors = listOf(validation.error ?: "Validation failed"),
                    message = "Repository validation failed"
                )
            }

            val repoPath = (validation as ValidationResult.Success).validatedPath

            // 2. Load .gitignore patterns
            val ignorePatterns = if (request.respectGitIgnore) {
                gitIgnoreService.loadIgnorePatterns(repoPath)
            } else {
                GitIgnorePatterns(emptyList())
            }

            // 3. Get repository info
            val repoInfo = getRepositoryInfo(repoPath)

            // 4. Process files
            val errors = mutableListOf<String>()
            val skippedFiles = mutableListOf<SkippedFile>()
            var totalChunks = 0
            var processedFiles = 0
            var filesScanned = 0
            var totalSize = 0L

            try {
                // Walk the repository
                val files = collectRepositoryFiles(
                    repoPath = repoPath,
                    ignorePatterns = ignorePatterns,
                    request = request,
                    skippedFiles = skippedFiles
                )

                filesScanned = files.size
                logger.info("Found ${files.size} files to process in repository")

                // Process each file
                for (fileContent in files) {
                    try {
                        totalSize += fileContent.content.length

                        // Check file size limit
                        val maxFileSize = (request.maxFileSizeMb ?: 5) * 1024 * 1024
                        if (fileContent.content.length > maxFileSize) {
                            skippedFiles.add(
                                SkippedFile(
                                    path = fileContent.path,
                                    reason = "too_large",
                                    details = "File size exceeds limit of ${request.maxFileSizeMb ?: 5}MB"
                                )
                            )
                            continue
                        }

                        // Scan for sensitive data in content
                        if (request.scanForSecrets) {
                            val detection = sensitiveDataDetector.containsSensitiveData(
                                fileContent.content,
                                fileContent.path
                            )

                            if (detection.detected && request.skipFilesWithSecrets) {
                                skippedFiles.add(
                                    SkippedFile(
                                        path = fileContent.path,
                                        reason = "sensitive_data",
                                        details = "Contains ${detection.matches.size} potential secret(s)"
                                    )
                                )
                                continue
                            }
                        }

                        // Delete existing embeddings for this file
                        val deleted = embeddingRepository.deleteByFilePath(fileContent.path)
                        if (deleted > 0) {
                            logger.debug("Deleted $deleted existing embeddings for ${fileContent.name}")
                        }

                        // Chunk the file
                        val chunks = chunkingService.chunkText(fileContent.content, fileContent.path)

                        if (chunks.isEmpty()) {
                            logger.warn("No chunks created for file: ${fileContent.path}")
                            skippedFiles.add(
                                SkippedFile(
                                    path = fileContent.path,
                                    reason = "no_chunks",
                                    details = "File produced no chunks"
                                )
                            )
                            continue
                        }

                        logger.debug("Created ${chunks.size} chunks for ${fileContent.name}")

                        // Generate embeddings and store
                        var successfulChunks = 0
                        for (chunk in chunks) {
                            val embedding: FloatArray? =
                                ollamaClient.generateEmbedding(chunk.text, request.model ?: "nomic-embed-text")

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
                            skippedFiles.add(
                                SkippedFile(
                                    path = fileContent.path,
                                    reason = "embedding_failed",
                                    details = "Failed to generate embeddings"
                                )
                            )
                        }

                    } catch (e: Exception) {
                        logger.error("Error processing file ${fileContent.path}", e)
                        errors.add("Error processing ${fileContent.name}: ${e.message}")
                        skippedFiles.add(
                            SkippedFile(
                                path = fileContent.path,
                                reason = "error",
                                details = e.message
                            )
                        )
                    }
                }

            } catch (e: Exception) {
                logger.error("Error during repository vectorization", e)
                errors.add("Critical error: ${e.message}")
            }

            val duration = System.currentTimeMillis() - startTime

            RepositoryVectorizeResponse(
                success = errors.isEmpty(),
                filesProcessed = processedFiles,
                chunksCreated = totalChunks,
                filesSkipped = skippedFiles,
                errors = errors,
                message = if (errors.isEmpty()) {
                    "Successfully vectorized $processedFiles files ($totalChunks chunks)"
                } else {
                    "Vectorization completed with ${errors.size} error(s)"
                },
                metrics = VectorizationMetrics(
                    durationMs = duration,
                    totalSizeBytes = totalSize,
                    filesScanned = filesScanned
                ),
                repositoryInfo = repoInfo
            )
        }

    /**
     * Collects all files from repository respecting gitignore and filters.
     */
    private fun collectRepositoryFiles(
        repoPath: File,
        ignorePatterns: GitIgnorePatterns,
        request: RepositoryVectorizeRequest,
        skippedFiles: MutableList<SkippedFile>
    ): List<FileProcessingService.FileContent> {
        val files = mutableListOf<FileProcessingService.FileContent>()
        val maxFiles = request.maxFiles ?: 10_000

        repoPath.walkTopDown()
            .onEnter { dir ->
                // Skip .git directory
                if (dir.name == ".git") {
                    return@onEnter false
                }

                // Check if directory is ignored
                if (request.respectGitIgnore && gitIgnoreService.isIgnored(dir, repoPath, ignorePatterns)) {
                    logger.debug("Skipping ignored directory: ${dir.relativeTo(repoPath).path}")
                    return@onEnter false
                }

                true
            }
            .filter { it.isFile }
            .forEach { file ->
                // Check file count limit
                if (files.size >= maxFiles) {
                    logger.warn("Reached max file limit: $maxFiles")
                    return@forEach
                }

                // Check if file is ignored by gitignore
                if (request.respectGitIgnore && gitIgnoreService.isIgnored(file, repoPath, ignorePatterns)) {
                    skippedFiles.add(
                        SkippedFile(
                            path = file.absolutePath,
                            reason = "gitignore",
                            details = "File matches .gitignore pattern"
                        )
                    )
                    return@forEach
                }

                // Check if file is sensitive by name/extension
                if (request.scanForSecrets && sensitiveDataDetector.isSensitiveFile(file)) {
                    skippedFiles.add(
                        SkippedFile(
                            path = file.absolutePath,
                            reason = "sensitive_file",
                            details = "File marked as sensitive (extension or name)"
                        )
                    )
                    return@forEach
                }

                // Check if it's a text file
                if (!isTextFile(file)) {
                    skippedFiles.add(
                        SkippedFile(
                            path = file.absolutePath,
                            reason = "binary",
                            details = "Not a recognized text file type"
                        )
                    )
                    return@forEach
                }

                // Read file content
                try {
                    val content = file.readText(StandardCharsets.UTF_8)
                    files.add(
                        FileProcessingService.FileContent(
                            path = file.absolutePath,
                            name = file.name,
                            content = content,
                            extension = file.extension
                        )
                    )
                } catch (e: Exception) {
                    logger.error("Failed to read file: ${file.absolutePath}", e)
                    skippedFiles.add(
                        SkippedFile(
                            path = file.absolutePath,
                            reason = "read_error",
                            details = "Failed to read file: ${e.message}"
                        )
                    )
                }
            }

        return files
    }

    /**
     * Checks if a file is a text file based on extension.
     */
    private fun isTextFile(file: File): Boolean {
        return file.extension.lowercase() in FileProcessingService.TEXT_FILE_EXTENSIONS
    }

    /**
     * Extracts repository information using JGit.
     */
    private fun getRepositoryInfo(repoPath: File): RepositoryInfo? {
        return try {
            val git = Git.open(repoPath)
            val repository = git.repository

            val branch = repository.branch
            val head = repository.resolve("HEAD")
            val commitHash = head?.name

            val config = repository.config
            val remoteUrl = config.getString("remote", "origin", "url")

            git.close()

            RepositoryInfo(
                branch = branch,
                commitHash = commitHash,
                remoteUrl = remoteUrl
            )
        } catch (e: Exception) {
            logger.warn("Failed to get repository info", e)
            null
        }
    }
}
