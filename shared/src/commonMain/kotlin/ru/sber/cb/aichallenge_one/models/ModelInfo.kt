package ru.sber.cb.aichallenge_one.models

import kotlinx.serialization.Serializable

@Serializable
data class ModelInfo(
    val id: String,
    val name: String
)
