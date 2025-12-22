package ru.sber.cb.aichallenge_one.vectorizer.models

data class ChunkMetadata(
    val filePath: String,
    val startTokenIndex: Int,
    val endTokenIndex: Int
)
