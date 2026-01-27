package ru.sber.cb.aichallenge_one.rag

import com.typesafe.config.ConfigFactory
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.koin.core.logger.Level
import org.koin.ktor.plugin.Koin
import ru.sber.cb.aichallenge_one.rag.database.DatabaseFactory
import ru.sber.cb.aichallenge_one.rag.di.ragModule
import ru.sber.cb.aichallenge_one.rag.routing.ragRouting

const val RAG_SERVER_PORT = 8091

fun main() {
    embeddedServer(Netty, port = RAG_SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // Load configuration (priority: env vars > system props > application.conf)
    val config = ConfigFactory.systemEnvironment()
        .withFallback(ConfigFactory.systemProperties())
        .withFallback(ConfigFactory.load())
        .resolve()

    log.info("RAG Server starting on port $RAG_SERVER_PORT")

    // Get vectorizer URL from config
    val vectorizerUrl = config.getString("vectorizer.url")

    // Install Koin DI
    install(Koin) {
        printLogger(Level.INFO)
        modules(ragModule(vectorizerUrl))
    }

    // Initialize database
    try {
        DatabaseFactory.init(config)
        log.info("Database initialized successfully")
    } catch (e: Exception) {
        log.error("Failed to initialize database", e)
        throw e
    }

    // Install plugins
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            isLenient = true
        })
    }

    // CORS (development only!)
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        anyHost()  // WARNING: Restrict in production!
    }

    // Configure routing
    routing {
        get("/") {
            call.respondText("RAG Service is running on port $RAG_SERVER_PORT")
        }

        ragRouting()
    }
}
