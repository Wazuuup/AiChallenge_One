package ru.sber.cb.aichallenge_one.models.vectorizer

import kotlinx.serialization.Serializable

@Serializable
data class RepositoryVectorizeResponse(
    val success: Boolean,
    val filesProcessed: Int,
    val chunksCreated: Int,
    val filesSkipped: List<SkippedFile>,
    val errors: List<String>,
    val message: String,
    val metrics: VectorizationMetrics? = null,
    val repositoryInfo: RepositoryInfo? = null
)

@Serializable
data class SkippedFile(
    val path: String,
    val reason: String,  // "gitignore", "sensitive_data", "too_large", "binary", etc.
    val details: String? = null
)

@Serializable
data class VectorizationMetrics(
    val durationMs: Long,
    val totalSizeBytes: Long,
    val filesScanned: Int
)

@Serializable
data class RepositoryInfo(
    val branch: String?,
    val commitHash: String?,
    val remoteUrl: String?
)
