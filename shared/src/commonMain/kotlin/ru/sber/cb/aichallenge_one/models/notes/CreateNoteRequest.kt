package ru.sber.cb.aichallenge_one.models.notes

import kotlinx.serialization.Serializable

@Serializable
data class CreateNoteRequest(
    val text: String,
    val dueDate: String? = null, // ISO-8601 format
    val priority: NotePriority = NotePriority.MEDIUM
)
