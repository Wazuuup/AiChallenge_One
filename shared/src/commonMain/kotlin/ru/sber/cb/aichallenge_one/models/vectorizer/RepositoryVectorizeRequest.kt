package ru.sber.cb.aichallenge_one.models.vectorizer

import kotlinx.serialization.Serializable

@Serializable
data class RepositoryVectorizeRequest(
    val repositoryPath: String,
    val model: String? = "nomic-embed-text",

    // Advanced options
    val respectGitIgnore: Boolean = true,
    val scanForSecrets: Boolean = true,
    val skipFilesWithSecrets: Boolean = true,
    val maxFiles: Int? = null,
    val maxFileSizeMb: Int? = null
)
