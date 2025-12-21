package ru.sber.cb.aichallenge_one.models.notes

import kotlinx.serialization.Serializable

@Serializable
data class NoteResponse(
    val note: Note? = null,
    val message: String? = null,
    val success: Boolean
)
