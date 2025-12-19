package ru.sber.cb.aichallenge_one.news.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import ru.sber.cb.aichallenge_one.models.news.CreateArticleRequest
import ru.sber.cb.aichallenge_one.models.news.UpdateArticleRequest
import ru.sber.cb.aichallenge_one.news.service.NewsService

fun Application.configureNewsRouting() {
    routing {
        newsRoutes()
    }
}

fun Route.newsRoutes() {
    val newsService by inject<NewsService>()

    route("/api/news") {
        // GET /api/news - Get all articles with pagination
        get {
            try {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0

                val articles = newsService.getAllArticles(limit, offset)
                call.respond(HttpStatusCode.OK, articles)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        // GET /api/news/search?q=query - Search articles
        get("/search") {
            try {
                val query = call.request.queryParameters["q"]
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Query parameter 'q' is required")
                    )
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100

                val articles = newsService.searchArticles(query, limit)
                call.respond(HttpStatusCode.OK, articles)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        // GET /api/news/{id} - Get article by ID
        get("/{id}") {
            try {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid article ID"))

                val article = newsService.getArticleById(id)
                if (article != null) {
                    call.respond(HttpStatusCode.OK, article)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Article not found"))
                }
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        // POST /api/news - Create new article
        post {
            try {
                val request = call.receive<CreateArticleRequest>()
                val article = newsService.createArticle(request)
                call.respond(HttpStatusCode.Created, article)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        // PUT /api/news/{id} - Update article
        put("/{id}") {
            try {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid article ID"))

                val request = call.receive<UpdateArticleRequest>()
                val article = newsService.updateArticle(id, request)

                if (article != null) {
                    call.respond(HttpStatusCode.OK, article)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Article not found"))
                }
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }

        // DELETE /api/news/{id} - Delete article
        delete("/{id}") {
            try {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid article ID"))

                val deleted = newsService.deleteArticle(id)
                if (deleted) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Article not found"))
                }
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
            }
        }
    }
}
