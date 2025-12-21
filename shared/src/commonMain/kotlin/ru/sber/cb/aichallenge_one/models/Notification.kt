package ru.sber.cb.aichallenge_one.models

import kotlinx.serialization.Serializable

@Serializable
data class Notification(
    val id: String,
    val text: String,
    val timestamp: Long,
    val isRead: Boolean = false
)

@Serializable
data class NotificationsResponse(
    val notifications: List<Notification>,
    val count: Int
)

@Serializable
data class MarkReadResponse(
    val success: Boolean,
    val message: String = ""
)

@Serializable
data class SummaryGenerationResponse(
    val success: Boolean,
    val message: String,
    val notificationId: String? = null
)
