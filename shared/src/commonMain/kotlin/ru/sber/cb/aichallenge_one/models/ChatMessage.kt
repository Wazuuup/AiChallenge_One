package ru.sber.cb.aichallenge_one.models

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val text: String,
    val sender: SenderType
)
