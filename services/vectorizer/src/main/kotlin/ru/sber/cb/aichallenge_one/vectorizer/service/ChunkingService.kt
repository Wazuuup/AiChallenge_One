package ru.sber.cb.aichallenge_one.vectorizer.service

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingRegistry
import com.knuddels.jtokkit.api.EncodingType
import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.vectorizer.models.ChunkMetadata
import ru.sber.cb.aichallenge_one.vectorizer.models.TextChunk

class ChunkingService {
    private val logger = LoggerFactory.getLogger(ChunkingService::class.java)

    companion object {
        const val TARGET_CHUNK_SIZE = 500
        const val MAX_CHUNK_SIZE = 700
        const val MIN_CHUNK_SIZE = 200
        const val OVERLAP_SIZE = 75
    }

    private val registry: EncodingRegistry = Encodings.newDefaultEncodingRegistry()
    private val encoding: Encoding = registry.getEncoding(EncodingType.CL100K_BASE)

    fun chunkText(text: String, filePath: String): List<TextChunk> {
        if (text.isBlank()) return emptyList()

        val chunks = mutableListOf<TextChunk>()
        var chunkIndex = 0

        // Split by paragraphs first
        val paragraphs = text.split(Regex("\n\n+"))
        var currentChunk = StringBuilder()
        var currentTokens = 0

        for (paragraph in paragraphs) {
            val paragraphTokens = countTokens(paragraph)

            // If single paragraph exceeds max, split by sentences
            if (paragraphTokens > MAX_CHUNK_SIZE) {
                // Flush current chunk if exists
                if (currentChunk.isNotEmpty()) {
                    chunks.add(createChunk(currentChunk.toString(), chunkIndex++, filePath))
                    currentChunk = StringBuilder()
                    currentTokens = 0
                }

                // Split large paragraph by sentences
                chunks.addAll(splitBySentences(paragraph, chunkIndex, filePath))
                chunkIndex = chunks.size
                continue
            }

            // Check if adding paragraph would exceed max
            if (currentTokens + paragraphTokens > MAX_CHUNK_SIZE && currentChunk.isNotEmpty()) {
                // Finalize current chunk
                chunks.add(createChunk(currentChunk.toString(), chunkIndex++, filePath))

                // Apply overlap
                currentChunk = StringBuilder(getOverlapText(currentChunk.toString()))
                currentTokens = countTokens(currentChunk.toString())
            }

            // Add paragraph to current chunk
            if (currentChunk.isNotEmpty()) {
                currentChunk.append("\n\n")
                currentTokens += 2 // Approximate tokens for newlines
            }
            currentChunk.append(paragraph)
            currentTokens = countTokens(currentChunk.toString())

            // If we've reached target, create chunk
            if (currentTokens >= TARGET_CHUNK_SIZE) {
                chunks.add(createChunk(currentChunk.toString(), chunkIndex++, filePath))

                // Apply overlap
                currentChunk = StringBuilder(getOverlapText(currentChunk.toString()))
                currentTokens = countTokens(currentChunk.toString())
            }
        }

        // Add remaining text
        if (currentChunk.isNotEmpty()) {
            val remainingTokens = countTokens(currentChunk.toString())

            if (remainingTokens >= MIN_CHUNK_SIZE) {
                chunks.add(createChunk(currentChunk.toString(), chunkIndex, filePath))
            } else if (chunks.isNotEmpty()) {
                // Merge with last chunk if too small
                val lastChunk = chunks.removeLast()
                val merged = lastChunk.text + "\n\n" + currentChunk.toString()
                val mergedTokens = countTokens(merged)

                if (mergedTokens <= MAX_CHUNK_SIZE) {
                    chunks.add(
                        TextChunk(
                            text = merged,
                            chunkIndex = lastChunk.chunkIndex,
                            tokenCount = mergedTokens,
                            metadata = lastChunk.metadata
                        )
                    )
                } else {
                    // Can't merge, keep as separate chunks
                    chunks.add(lastChunk)
                    chunks.add(createChunk(currentChunk.toString(), chunkIndex, filePath))
                }
            } else {
                // First and only chunk, add even if below minimum
                chunks.add(createChunk(currentChunk.toString(), chunkIndex, filePath))
            }
        }

        return chunks
    }

    private fun splitBySentences(text: String, startIndex: Int, filePath: String): List<TextChunk> {
        val chunks = mutableListOf<TextChunk>()
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        var currentChunk = StringBuilder()
        var currentTokens = 0
        var chunkIndex = startIndex

        for (sentence in sentences) {
            val sentenceTokens = countTokens(sentence)

            // If single sentence exceeds max, split by tokens
            if (sentenceTokens > MAX_CHUNK_SIZE) {
                if (currentChunk.isNotEmpty()) {
                    chunks.add(createChunk(currentChunk.toString(), chunkIndex++, filePath))
                    currentChunk = StringBuilder()
                    currentTokens = 0
                }
                chunks.addAll(splitByTokens(sentence, chunkIndex, filePath))
                chunkIndex = startIndex + chunks.size
                continue
            }

            if (currentTokens + sentenceTokens > MAX_CHUNK_SIZE && currentChunk.isNotEmpty()) {
                chunks.add(createChunk(currentChunk.toString(), chunkIndex++, filePath))
                currentChunk = StringBuilder(getOverlapText(currentChunk.toString()))
                currentTokens = countTokens(currentChunk.toString())
            }

            if (currentChunk.isNotEmpty()) {
                currentChunk.append(" ")
            }
            currentChunk.append(sentence)
            currentTokens = countTokens(currentChunk.toString())

            if (currentTokens >= TARGET_CHUNK_SIZE) {
                chunks.add(createChunk(currentChunk.toString(), chunkIndex++, filePath))
                currentChunk = StringBuilder(getOverlapText(currentChunk.toString()))
                currentTokens = countTokens(currentChunk.toString())
            }
        }

        if (currentChunk.isNotEmpty()) {
            chunks.add(createChunk(currentChunk.toString(), chunkIndex, filePath))
        }

        return chunks
    }

    private fun splitByTokens(text: String, startIndex: Int, filePath: String): List<TextChunk> {
        val chunks = mutableListOf<TextChunk>()
        val tokensList = encoding.encode(text)
        var chunkIndex = startIndex

        var i = 0
        var tokenCount = 0
        // Count tokens manually since size is private
        while (tokenCount < tokensList.size()) {
            tokenCount++
        }

        while (i < tokenCount) {
            val endIndex = minOf(i + TARGET_CHUNK_SIZE, tokenCount)
            // Extract tokens by decoding a substring and re-encoding
            val fullDecoded = encoding.decode(tokensList)
            // For simplicity, split by characters as a workaround
            val chunkText = if (tokenCount < MAX_CHUNK_SIZE) {
                fullDecoded
            } else {
                // Fallback: split text by approximate position
                val startPos = (i * fullDecoded.length) / tokenCount
                val endPos = (endIndex * fullDecoded.length) / tokenCount
                fullDecoded.substring(startPos, endPos.coerceAtMost(fullDecoded.length))
            }

            val actualTokenCount = countTokens(chunkText)

            chunks.add(
                TextChunk(
                    text = chunkText,
                    chunkIndex = chunkIndex++,
                    tokenCount = actualTokenCount,
                    metadata = ChunkMetadata(
                        filePath = filePath,
                        startTokenIndex = i,
                        endTokenIndex = endIndex
                    )
                )
            )

            // Move forward with overlap
            i += TARGET_CHUNK_SIZE - OVERLAP_SIZE
            if (i < 0) i = 0  // Safety check
        }

        return chunks
    }

    private fun getOverlapText(text: String): String {
        val tokenCount = countTokens(text)
        if (tokenCount <= OVERLAP_SIZE) return text

        // Split text by word boundaries and approximate overlap
        val words = text.split(Regex("\\s+"))
        val approxWordsPerToken = words.size.toDouble() / tokenCount
        val overlapWords = (OVERLAP_SIZE * approxWordsPerToken).toInt().coerceAtLeast(1)

        val overlapText = words.takeLast(overlapWords).joinToString(" ")
        return overlapText
    }

    private fun countTokens(text: String): Int {
        return try {
            encoding.countTokens(text)
        } catch (e: Exception) {
            logger.warn("Failed to count tokens, using approximation", e)
            // Fallback approximation: ~4 chars per token
            (text.length / 4).coerceAtLeast(1)
        }
    }

    private fun createChunk(text: String, index: Int, filePath: String): TextChunk {
        return TextChunk(
            text = text,
            chunkIndex = index,
            tokenCount = countTokens(text),
            metadata = ChunkMetadata(
                filePath = filePath,
                startTokenIndex = 0,
                endTokenIndex = 0
            )
        )
    }
}
