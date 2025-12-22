package ru.sber.cb.aichallenge_one.vectorizer

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
import ru.sber.cb.aichallenge_one.vectorizer.database.DatabaseFactory
import ru.sber.cb.aichallenge_one.vectorizer.di.vectorizerModule
import ru.sber.cb.aichallenge_one.vectorizer.routing.vectorizerRouting

const val VECTORIZER_SERVER_PORT = 8090

fun main() {
    embeddedServer(Netty, port = VECTORIZER_SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // Load configuration
    val config = ConfigFactory.systemEnvironment()
        .withFallback(ConfigFactory.systemProperties())
        .withFallback(ConfigFactory.load())
        .resolve()

    log.info("Vectorizer Server starting on port $VECTORIZER_SERVER_PORT")

    val ollamaBaseUrl = if (config.hasPath("ollama.baseUrl")) {
        config.getString("ollama.baseUrl")
    } else {
        "http://localhost:11434"
    }

    // Install Koin
    install(Koin) {
        printLogger(Level.INFO)
        modules(vectorizerModule(ollamaBaseUrl))
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

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        anyHost() // WARNING: Development only!
    }

    // Configure routing
    routing {
        get("/") {
            call.respondText("Vectorizer Service is running on port $VECTORIZER_SERVER_PORT")
        }

        vectorizerRouting()
    }
}
