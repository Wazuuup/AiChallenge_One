package ru.sber.cb.aichallenge_one.rag.routing

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import ru.sber.cb.aichallenge_one.models.rag.SearchRequest
import ru.sber.cb.aichallenge_one.models.rag.SearchResponse
import ru.sber.cb.aichallenge_one.rag.service.RagService

fun Route.ragRouting() {
    val ragService by inject<RagService>()

    route("/api/rag") {
        // Search similar chunks
        post("/search") {
            try {
                val request = call.receive<SearchRequest>()
                val results = ragService.searchSimilar(request.query, request.limit)
                call.respond(HttpStatusCode.OK, SearchResponse(results))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                call.application.environment.log.error("Error searching similar embeddings", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Internal server error"))
                )
            }
        }
    }
}
