package ru.sber.cb.aichallenge_one.vectorizer.models

data class TextChunk(
    val text: String,
    val chunkIndex: Int,
    val tokenCount: Int,
    val metadata: ChunkMetadata
)
