package ru.sber.cb.aichallenge_one.models.vectorizer

import kotlinx.serialization.Serializable

@Serializable
data class FolderVectorizeRequest(
    val folderPath: String,
    val model: String? = "nomic-embed-text"
)
