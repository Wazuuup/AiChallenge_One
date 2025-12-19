package ru.sber.cb.aichallenge_one.notes

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
import ru.sber.cb.aichallenge_one.notes.database.DatabaseFactory
import ru.sber.cb.aichallenge_one.notes.di.notesModule
import ru.sber.cb.aichallenge_one.notes.routing.notesRouting

const val NOTES_SERVER_PORT = 8084

fun main() {
    embeddedServer(Netty, port = NOTES_SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // Load configuration using Typesafe Config
    val config = ConfigFactory.systemEnvironment()
        .withFallback(ConfigFactory.systemProperties())
        .withFallback(ConfigFactory.load())
        .resolve()

    log.info("Notes Server starting on port $NOTES_SERVER_PORT")

    // Install Koin for dependency injection
    install(Koin) {
        printLogger(Level.INFO)
        modules(notesModule())
    }

    // Initialize database
    try {
        DatabaseFactory.init(config)
        log.info("Database initialized successfully")
    } catch (e: Exception) {
        log.error("Failed to initialize database", e)
        throw e
    }

    // Install Content Negotiation for JSON
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            isLenient = true
        })
    }

    // Install CORS (for development - restrict in production)
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        anyHost() // WARNING: Development only!
    }

    // Configure routing
    routing {
        get("/") {
            call.respondText("Notes CRUD Server is running on port $NOTES_SERVER_PORT")
        }

        notesRouting()
    }
}
