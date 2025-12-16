package ru.sber.cb.aichallenge_one.mcp_server.model

import kotlinx.serialization.Serializable

/**
 * Response from CBR (Central Bank of Russia) API
 * Source: https://www.cbr-xml-daily.ru/daily_json.js
 */
@Serializable
data class CbrResponse(
    val Date: String,
    val PreviousDate: String,
    val PreviousURL: String,
    val Timestamp: String,
    val Valute: Map<String, CurrencyInfo>
)

/**
 * Information about a specific currency
 */
@Serializable
data class CurrencyInfo(
    val ID: String,
    val NumCode: String,
    val CharCode: String,
    val Nominal: Int,
    val Name: String,
    val Value: Double,
    val Previous: Double
)
