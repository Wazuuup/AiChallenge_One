package ru.sber.cb.aichallenge_one.mcp_server.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import ru.sber.cb.aichallenge_one.mcp_server.model.CbrResponse
import ru.sber.cb.aichallenge_one.mcp_server.model.CurrencyInfo

/**
 * Service for fetching and parsing currency exchange rates from CBR (Central Bank of Russia)
 */
class CurrencyExchangeService {
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            val jsonConfig = Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
            // Register JSON parser for both application/json and application/javascript
            json(jsonConfig, contentType = ContentType.Application.Json)
            json(jsonConfig, contentType = ContentType.Application.JavaScript)
        }
    }

    private val cbrApiUrl = "https://www.cbr-xml-daily.ru/daily_json.js"

    /**
     * Fetches currency exchange rate for the specified currency code
     * @param currencyCode Three-letter currency code (e.g., "USD", "EUR", "CNY")
     * @return CurrencyInfo if found, null otherwise
     */
    suspend fun getExchangeRate(currencyCode: String): CurrencyInfo? {
        return try {
            val response: CbrResponse = httpClient.get(cbrApiUrl).body()
            response.Valute[currencyCode.uppercase()]
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fetches all available currencies and their exchange rates
     * @return CbrResponse containing all currency data
     */
    suspend fun getAllExchangeRates(): CbrResponse? {
        return try {
            httpClient.get(cbrApiUrl).body()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Clean up resources
     */
    fun close() {
        httpClient.close()
    }
}
