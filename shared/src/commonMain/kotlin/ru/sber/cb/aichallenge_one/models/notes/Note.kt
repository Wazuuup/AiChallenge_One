package ru.sber.cb.aichallenge_one.models.notes

import kotlinx.serialization.Serializable

@Serializable
data class Note(
    val id: Int,
    val text: String,
    val createdAt: String, // ISO-8601 format
    val dueDate: String? = null, // ISO-8601 format
    val priority: NotePriority = NotePriority.MEDIUM,
    val completed: Boolean = false
)
