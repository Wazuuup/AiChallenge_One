package ru.sber.cb.aichallenge_one.github.webhook.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WebhookPayload(
    val action: String, // "opened", "synchronize", "closed", etc.
    @SerialName("pull_request") val pullRequest: PullRequestInfo,
    val repository: RepositoryInfo
)

@Serializable
data class PullRequestInfo(
    val number: Int,
    val title: String,
    @SerialName("diff_url") val diffUrl: String,
    val state: String, // "open", "closed"
    val head: BranchInfo,
    val base: BranchInfo
)

@Serializable
data class BranchInfo(
    val ref: String, // branch name
    val sha: String  // commit SHA
)

@Serializable
data class RepositoryInfo(
    @SerialName("full_name") val fullName: String, // "owner/repo"
    val owner: OwnerInfo,
    val name: String
)

@Serializable
data class OwnerInfo(
    val login: String
)
