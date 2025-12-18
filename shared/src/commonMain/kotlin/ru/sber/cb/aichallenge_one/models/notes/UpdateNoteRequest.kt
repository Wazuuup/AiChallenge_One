package ru.sber.cb.aichallenge_one.models.notes

import kotlinx.serialization.Serializable

@Serializable
data class UpdateNoteRequest(
    val text: String? = null,
    val dueDate: String? = null, // ISO-8601 format or null to clear
    val priority: NotePriority? = null,
    val completed: Boolean? = null
)
