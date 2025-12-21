package ru.sber.cb.aichallenge_one.models.notes

import kotlinx.serialization.Serializable

@Serializable
enum class NotePriority {
    LOW,
    MEDIUM,
    HIGH
}
