package ru.sber.cb.aichallenge_one.models.tickets

import kotlinx.serialization.Serializable

@Serializable
data class UpdateTicketRequest(
    val title: String? = null,
    val description: String? = null,
    val initiator: String? = null,
    val priority: Int? = null, // 1-5
    val status: TicketStatus? = null
)
