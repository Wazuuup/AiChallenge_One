package ru.sber.cb.aichallenge_one.routing

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.client.OpenRouterSTTClient
import ru.sber.cb.aichallenge_one.models.TranscribeRequest
import ru.sber.cb.aichallenge_one.models.TranscribeResponse
import java.util.*

/**
 * Configure transcription routes for voice input
 *
 * This routing provides:
 * - POST /api/transcribe - Transcribe audio to text
 * - GET /api/transcribe/health - Check STT service availability
 */
fun Route.transcribeRoutes() {
    val sttClient by inject<OpenRouterSTTClient>()
    val logger = LoggerFactory.getLogger("TranscribeRouting")

    route("/api/transcribe") {
        /**
         * Transcribe audio to text
         *
         * Request body:
         * {
         *   "audioData": "base64_encoded_audio_string",
         *   "format": "webm",  // optional, default: "webm"
         *   "language": "auto"  // optional, default: "auto"
         * }
         *
         * Response:
         * {
         *   "text": "transcribed text",
         *   "status": "SUCCESS",
         *   "tokenUsage": {...},
         *   "duration": 3.5,
         *   "cost": 0.00035
         * }
         */
        post {
            try {
                // Check if STT client is configured
                if (sttClient == null) {
                    logger.warn("STT service not configured")
                    call.respond(
                        HttpStatusCode.ServiceUnavailable, TranscribeResponse(
                            text = "",
                            status = "ERROR",
                            tokenUsage = null,
                            duration = 0.0,
                            cost = 0.0,
                            error = "STT_SERVICE_NOT_CONFIGURED: OpenRouter API key not configured"
                        )
                    )
                    return@post
                }

                val request = call.receive<TranscribeRequest>()

                logger.info("Received transcription request: format=${request.format}, language=${request.language}")

                // Validate audio format
                val supportedFormats = setOf("webm", "wav", "mp3", "ogg", "flac", "m4a", "mp4", "mpeg")
                if (!supportedFormats.contains(request.format.lowercase())) {
                    logger.warn("Unsupported audio format: ${request.format}")
                    call.respond(
                        HttpStatusCode.BadRequest, TranscribeResponse(
                            text = "",
                            status = "ERROR",
                            tokenUsage = null,
                            duration = 0.0,
                            cost = 0.0,
                            error = "INVALID_AUDIO_FORMAT: Supported formats are ${supportedFormats.joinToString(", ")}"
                        )
                    )
                    return@post
                }

                // Decode base64 to validate
                val audioBytes = try {
                    Base64.getDecoder().decode(request.audioData)
                } catch (e: Exception) {
                    logger.warn("Invalid base64 encoding", e)
                    call.respond(
                        HttpStatusCode.BadRequest, TranscribeResponse(
                            text = "",
                            status = "ERROR",
                            tokenUsage = null,
                            duration = 0.0,
                            cost = 0.0,
                            error = "INVALID_BASE64: Failed to decode audio data"
                        )
                    )
                    return@post
                }

                // Check minimum size (at least 100 bytes)
                if (audioBytes.size < 100) {
                    logger.warn("Audio file too small: ${audioBytes.size} bytes")
                    call.respond(
                        HttpStatusCode.BadRequest, TranscribeResponse(
                            text = "",
                            status = "ERROR",
                            tokenUsage = null,
                            duration = 0.0,
                            cost = 0.0,
                            error = "AUDIO_TOO_SMALL: Audio file must be at least 100 bytes"
                        )
                    )
                    return@post
                }

                // Check size limit (25 MB for Whisper API)
                if (audioBytes.size > 25 * 1024 * 1024) {
                    logger.warn("Audio file too large: ${audioBytes.size} bytes")
                    call.respond(
                        HttpStatusCode.PayloadTooLarge, TranscribeResponse(
                            text = "",
                            status = "ERROR",
                            tokenUsage = null,
                            duration = 0.0,
                            cost = 0.0,
                            error = "AUDIO_TOO_LARGE: Maximum size is 25MB, got ${audioBytes.size / (1024 * 1024)}MB"
                        )
                    )
                    return@post
                }

                logger.info("Audio validation passed: size=${audioBytes.size} bytes, ~${audioBytes.size / 1024}KB")

                // Transcribe audio
                val response = sttClient?.transcribe(
                    audioData = request.audioData,
                    format = request.format,
                    language = request.language
                ) ?: TranscribeResponse(
                    text = "",
                    status = "ERROR",
                    tokenUsage = null,
                    duration = 0.0,
                    cost = 0.0,
                    error = "STT_CLIENT_NOT_INITIALIZED"
                )

                when (response.status) {
                    "SUCCESS" -> {
                        logger.info("Transcription successful: ${response.text.take(50)}...")
                        call.respond(HttpStatusCode.OK, response)
                    }

                    "ERROR" -> {
                        logger.error("Transcription failed: ${response.error}")
                        call.respond(
                            when {
                                response.error?.contains("STT_API_ERROR") == true -> HttpStatusCode.ServiceUnavailable
                                response.error?.contains("AUDIO_TOO_LARGE") == true -> HttpStatusCode.PayloadTooLarge
                                response.error?.contains("INVALID_BASE64") == true -> HttpStatusCode.BadRequest
                                else -> HttpStatusCode.InternalServerError
                            },
                            response
                        )
                    }

                    else -> {
                        logger.warn("Unknown transcription status: ${response.status}")
                        call.respond(
                            HttpStatusCode.InternalServerError, TranscribeResponse(
                                text = "",
                                status = "ERROR",
                                tokenUsage = null,
                                duration = 0.0,
                                cost = 0.0,
                                error = "UNKNOWN_STATUS: ${response.status}"
                            )
                        )
                    }
                }

            } catch (e: Exception) {
                logger.error("Error in transcribe endpoint", e)
                call.respond(
                    HttpStatusCode.InternalServerError, TranscribeResponse(
                        text = "",
                        status = "ERROR",
                        tokenUsage = null,
                        duration = 0.0,
                        cost = 0.0,
                        error = "INTERNAL_ERROR: ${e.message}"
                    )
                )
            }
        }

        /**
         * Health check endpoint for STT service
         *
         * Response:
         * {
         *   "available": true,
         *   "message": "STT service is available"
         * }
         */
        get("/health") {
            try {
                if (sttClient == null) {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable, mapOf(
                            "available" to false,
                            "message" to "STT service not configured"
                        )
                    )
                    return@get
                }

                val isAvailable = sttClient?.isAvailable() ?: false

                if (isAvailable) {
                    call.respond(
                        HttpStatusCode.OK, mapOf(
                            "available" to true,
                            "message" to "STT service is available"
                        )
                    )
                } else {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable, mapOf(
                            "available" to false,
                            "message" to "STT service is not available"
                        )
                    )
                }
            } catch (e: Exception) {
                logger.error("Error checking STT service health", e)
                call.respond(
                    HttpStatusCode.ServiceUnavailable, mapOf(
                        "available" to false,
                        "message" to "Failed to check STT service: ${e.message}"
                    )
                )
            }
        }
    }
}
