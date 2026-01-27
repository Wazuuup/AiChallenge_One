package ru.sber.cb.aichallenge_one.client

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.models.TranscribeResponse
import java.util.*

/**
 * Response from OpenAI Whisper API
 */
@Serializable
data class WhisperResponse(
    val text: String
)

/**
 * OpenRouter STT (Speech-to-Text) Client
 *
 * This client is designed to use OpenAI-compatible Whisper API for audio transcription.
 * Currently, OpenRouter doesn't support audio transcription, so this implementation
 * provides a stub that can be extended when OpenRouter adds Whisper support.
 *
 * For production use with audio transcription, you would typically:
 * 1. Use OpenAI's Whisper API directly with an OpenAI API key
 * 2. Use a dedicated STT service
 * 3. Wait for OpenRouter to add Whisper support
 *
 * @param httpClient Ktor HTTP client instance
 * @param baseUrl Base URL of the API
 * @param apiKey API key for authentication
 * @param model Model to use for transcription (default: "whisper-1")
 */
class OpenRouterSTTClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String = "whisper-1"
) {
    private val logger = LoggerFactory.getLogger(OpenRouterSTTClient::class.java)

    companion object {
        // Whisper pricing (as of 2024)
        private const val WHISPER_COST_PER_MINUTE = 0.006 // $0.006 per minute
    }

    /**
     * Transcribe audio data to text
     *
     * Note: This is a stub implementation that returns an error indicating
     * that OpenRouter doesn't currently support audio transcription.
     * When OpenRouter adds Whisper support, this method can be updated to use it.
     *
     * @param audioData Base64 encoded audio data
     * @param format Audio format (webm, wav, mp3, etc.)
     * @param language Language code (e.g., "en", "ru", "auto" for auto-detection)
     * @return TranscribeResponse with transcribed text or error
     */
    suspend fun transcribe(
        audioData: String,
        format: String = "webm",
        language: String = "auto"
    ): TranscribeResponse {
        try {
            logger.info("Starting audio transcription: format=$format, language=$language")

            // Decode base64 audio data
            val audioBytes = try {
                Base64.getDecoder().decode(audioData)
            } catch (e: Exception) {
                logger.error("Failed to decode base64 audio data", e)
                return TranscribeResponse(
                    text = "",
                    status = "ERROR",
                    tokenUsage = null,
                    duration = 0.0,
                    cost = 0.0,
                    error = "INVALID_BASE64: ${e.message}"
                )
            }

            // Check audio size limit (25 MB for Whisper API)
            if (audioBytes.size > 25 * 1024 * 1024) {
                logger.error("Audio file too large: ${audioBytes.size} bytes")
                return TranscribeResponse(
                    text = "",
                    status = "ERROR",
                    tokenUsage = null,
                    duration = 0.0,
                    cost = 0.0,
                    error = "AUDIO_TOO_LARGE: Maximum size is 25MB"
                )
            }

            val startTime = System.currentTimeMillis()

            // Check if using OpenRouter (which doesn't support Whisper yet)
            if (baseUrl.contains("openrouter")) {
                logger.warn("OpenRouter doesn't currently support audio transcription")
                return TranscribeResponse(
                    text = "",
                    status = "ERROR",
                    tokenUsage = null,
                    duration = 0.0,
                    cost = 0.0,
                    error = "STT_NOT_SUPPORTED: OpenRouter doesn't currently support audio transcription. Please use OpenAI's Whisper API directly or another STT service."
                )
            }

            // For non-OpenRouter APIs (e.g., direct OpenAI), we could implement
            // the actual Whisper API call here using multipart/form-data
            // For now, this is a placeholder for when OpenRouter adds support

            val responseTimeMs = System.currentTimeMillis() - startTime

            logger.info("Transcription completed in ${responseTimeMs}ms (stub implementation)")

            // Placeholder response - to be implemented when OpenRouter adds support
            return TranscribeResponse(
                text = "",
                status = "ERROR",
                tokenUsage = null,
                duration = 0.0,
                cost = 0.0,
                error = "STT_NOT_IMPLEMENTED: Audio transcription is not yet implemented for this API. Please use OpenAI's Whisper API directly with your OpenAI API key."
            )

        } catch (e: Exception) {
            logger.error("Error during audio transcription", e)

            return TranscribeResponse(
                text = "",
                status = "ERROR",
                tokenUsage = null,
                duration = 0.0,
                cost = 0.0,
                error = "TRANSCRIPTION_ERROR: ${e.message}"
            )
        }
    }

    /**
     * Check if the STT service is available
     *
     * @return true if service is available, false otherwise
     */
    suspend fun isAvailable(): Boolean {
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/models") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $apiKey")
                }
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            logger.warn("STT service availability check failed", e)
            false
        }
    }
}
