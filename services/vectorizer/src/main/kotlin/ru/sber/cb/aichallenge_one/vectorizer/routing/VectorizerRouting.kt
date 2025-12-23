package ru.sber.cb.aichallenge_one.vectorizer.routing

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import ru.sber.cb.aichallenge_one.models.vectorizer.TextVectorizeRequest
import ru.sber.cb.aichallenge_one.models.vectorizer.TextVectorizeResponse
import ru.sber.cb.aichallenge_one.models.vectorizer.VectorizeRequest
import ru.sber.cb.aichallenge_one.models.vectorizer.VectorizeResponse
import ru.sber.cb.aichallenge_one.vectorizer.service.OllamaEmbeddingClient
import ru.sber.cb.aichallenge_one.vectorizer.service.VectorizerService

fun Route.vectorizerRouting() {
    val vectorizerService by inject<VectorizerService>()

    route("/api/vectorize") {
        post {
            try {
                val request = call.receive<TextVectorizeRequest>()

                if (request.text.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Text cannot be blank")
                    )
                    return@post
                }

                val ollamaClient by inject<OllamaEmbeddingClient>()
                val embedding = ollamaClient.generateEmbedding(
                    text = request.text,
                    model = request.model ?: "nomic-embed-text"
                )

                if (embedding != null) {
                    call.respond(
                        HttpStatusCode.OK,
                        TextVectorizeResponse(
                            embedding = embedding.toList(),
                            dimension = embedding.size,
                            model = request.model ?: "nomic-embed-text"
                        )
                    )
                } else {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Failed to generate embedding")
                    )
                }
            } catch (e: Exception) {
                call.application.environment.log.error("Error during vectorization", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }
    }

    route("/api/vectorizeFolder") {
        post {
            try {
                val request = call.receive<VectorizeRequest>()

                // Validate folder path
                if (request.folderPath.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        VectorizeResponse(
                            success = false,
                            filesProcessed = 0,
                            chunksCreated = 0,
                            filesSkipped = emptyList(),
                            errors = listOf("Folder path cannot be empty"),
                            message = "Invalid request"
                        )
                    )
                    return@post
                }

                call.application.environment.log.info("Vectorizing folder: ${request.folderPath}")

                val result = vectorizerService.vectorizeFolder(
                    folderPath = request.folderPath,
                    model = request.model ?: "nomic-embed-text"
                )

                val response = VectorizeResponse(
                    success = result.success,
                    filesProcessed = result.filesProcessed,
                    chunksCreated = result.chunksCreated,
                    filesSkipped = result.filesSkipped,
                    errors = result.errors,
                    message = if (result.success) {
                        "Successfully vectorized ${result.filesProcessed} files (${result.chunksCreated} chunks)"
                    } else {
                        "Vectorization completed with errors"
                    }
                )

                val statusCode = if (result.success) HttpStatusCode.OK else HttpStatusCode.PartialContent
                call.respond(statusCode, response)

            } catch (e: Exception) {
                call.application.environment.log.error("Error during vectorization", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    VectorizeResponse(
                        success = false,
                        filesProcessed = 0,
                        chunksCreated = 0,
                        filesSkipped = emptyList(),
                        errors = listOf(e.message ?: "Unknown error"),
                        message = "Internal server error"
                    )
                )
            }
        }
    }
}
