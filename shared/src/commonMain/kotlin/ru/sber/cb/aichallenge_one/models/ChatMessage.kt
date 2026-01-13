package ru.sber.cb.aichallenge_one.models

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class ChatMessage(
    val text: String,
    val sender: SenderType,
    val id: String = generateId()
) {
    companion object {
        private var counter = 0

        // ✅ Platform-independent ID generation
        private fun generateId(): String {
            return "msg-${counter++}-${Random.nextInt(0, Int.MAX_VALUE)}"
        }
    }
}
