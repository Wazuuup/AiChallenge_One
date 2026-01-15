package ru.sber.cb.aichallenge_one.models.tickets

import kotlinx.serialization.Serializable

@Serializable
data class Ticket(
    val id: Int,
    val title: String,
    val description: String,
    val initiator: String? = null,
    val priority: Int = 3, // 1-5 (1=low, 5=critical)
    val status: TicketStatus = TicketStatus.OPEN,
    val createdAt: String, // ISO-8601 format
    val updatedAt: String  // ISO-8601 format
)
