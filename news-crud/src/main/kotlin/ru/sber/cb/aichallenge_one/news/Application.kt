package ru.sber.cb.aichallenge_one.news

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
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import ru.sber.cb.aichallenge_one.news.database.DatabaseFactory
import ru.sber.cb.aichallenge_one.news.di.newsAppModule
import ru.sber.cb.aichallenge_one.news.routing.configureNewsRouting

fun main() {
    embeddedServer(Netty, port = 8081, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // Load configuration using Typesafe Config with system properties and environment variables
    val config = ConfigFactory.systemEnvironment()
        .withFallback(ConfigFactory.systemProperties())
        .withFallback(ConfigFactory.load())
        .resolve()

    // Database configuration
    val dbUrl = if (config.hasPath("database.url")) {
        config.getString("database.url")
    } else {
        "jdbc:postgresql://localhost:5432/newsdb"
    }

    val dbDriver = if (config.hasPath("database.driver")) {
        config.getString("database.driver")
    } else {
        "org.postgresql.Driver"
    }

    val dbUser = if (config.hasPath("database.user")) {
        config.getString("database.user")
    } else {
        "postgres"
    }

    val dbPassword = if (config.hasPath("database.password")) {
        config.getString("database.password")
    } else {
        "postgres"
    }

    val dbMaxPoolSize = if (config.hasPath("database.maxPoolSize")) {
        config.getInt("database.maxPoolSize")
    } else {
        10
    }

    // Initialize database
    DatabaseFactory.init(
        jdbcUrl = dbUrl,
        driverClassName = dbDriver,
        username = dbUser,
        password = dbPassword,
        maximumPoolSize = dbMaxPoolSize
    )

    // Install Koin for DI
    install(Koin) {
        slf4jLogger()
        modules(newsAppModule)
    }

    // Install ContentNegotiation for JSON
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    // Install CORS
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        anyHost() // For development - restrict in production!
    }

    // Configure routing
    routing {
        get("/") {
            call.respondText("News CRUD Server is running", ContentType.Text.Plain)
        }
    }

    configureNewsRouting()
}
