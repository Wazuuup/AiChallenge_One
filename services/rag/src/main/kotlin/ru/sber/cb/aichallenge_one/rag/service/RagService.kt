package ru.sber.cb.aichallenge_one.rag.service

import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.rag.repository.EmbeddingRepository

class RagService(
    private val embeddingRepository: EmbeddingRepository,
    private val vectorizerClient: VectorizerClient
) {
    private val logger = LoggerFactory.getLogger(RagService::class.java)

    suspend fun searchSimilar(query: String, limit: Int = 10): List<String> {
        require(query.isNotBlank()) { "Query cannot be blank" }
        require(limit > 0) { "Limit must be positive" }
        require(limit <= 100) { "Limit cannot exceed 100" }

        logger.info("Searching for similar embeddings with query: '$query', limit: $limit")

        // Convert query to vector
        val queryVector = vectorizerClient.vectorize(query)
            ?: throw Exception("Failed to vectorize query")

        // Search for similar chunks in embeddings table
        return embeddingRepository.searchSimilar(queryVector, limit)
    }
}
