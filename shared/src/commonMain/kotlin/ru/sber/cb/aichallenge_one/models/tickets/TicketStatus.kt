package ru.sber.cb.aichallenge_one.models.tickets

import kotlinx.serialization.Serializable

@Serializable
enum class TicketStatus {
    OPEN,
    CLOSED
}
