package ru.sber.cb.aichallenge_one.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import ru.sber.cb.aichallenge_one.models.ChatResponse
import ru.sber.cb.aichallenge_one.models.SendMessageRequest

class ChatApi {
    private val client = HttpClient(Js) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
    }

    private val serverUrl = "http://localhost:${Constants.SERVER_PORT.number}"

    suspend fun sendMessage(text: String, systemPrompt: String = "", temperature: Double = 0.7): ChatResponse {
        return try {
            val response = client.post("$serverUrl/api/send-message") {
                contentType(ContentType.Application.Json)
                setBody(SendMessageRequest(text, systemPrompt, temperature))
            }
            response.body()
        } catch (e: Exception) {
            println("Error sending message: $e")
            throw e
        }
    }

    suspend fun clearHistory() {
        try {
            client.post("$serverUrl/api/clear-history") {
                contentType(ContentType.Application.Json)
            }
        } catch (e: Exception) {
            println("Error clearing history: $e")
            throw e
        }
    }
}
