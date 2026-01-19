package ru.sber.cb.aichallenge_one

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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.core.logger.Level
import org.koin.ktor.ext.getKoin
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import ru.sber.cb.aichallenge_one.database.DatabaseFactory
import ru.sber.cb.aichallenge_one.di.appModule
import ru.sber.cb.aichallenge_one.routing.chatRouting
import ru.sber.cb.aichallenge_one.routing.configureToolCallingRouting
import ru.sber.cb.aichallenge_one.routing.modelsRouting
import ru.sber.cb.aichallenge_one.routing.notificationRouting
import ru.sber.cb.aichallenge_one.service.ChatService
import ru.sber.cb.aichallenge_one.service.mcp.IMcpClientService

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // Load configuration using Typesafe Config with system properties and environment variables
    val config = ConfigFactory.systemEnvironment()
        .withFallback(ConfigFactory.systemProperties())
        .withFallback(ConfigFactory.load())
        .resolve()

    val gigaChatBaseUrl = if (config.hasPath("gigachat.baseUrl")) {
        config.getString("gigachat.baseUrl")
    } else {
        "https://gigachat.devices.sberbank.ru/api/v1"
    }

    val gigaChatAuthUrl = if (config.hasPath("gigachat.authUrl")) {
        config.getString("gigachat.authUrl")
    } else {
        "https://ngw.devices.sberbank.ru:9443/api/v2/oauth"
    }

    val clientId = if (config.hasPath("gigachat.clientId")) {
        config.getString("gigachat.clientId")
    } else {
        throw IllegalStateException("gigachat.clientId not found in configuration. Please set GIGACHAT_CLIENT_ID environment variable or configure it in application.conf")
    }

    val clientSecret = if (config.hasPath("gigachat.clientSecret")) {
        config.getString("gigachat.clientSecret")
    } else {
        throw IllegalStateException("gigachat.clientSecret not found in configuration. Please set GIGACHAT_CLIENT_SECRET environment variable or configure it in application.conf")
    }

    val scope = if (config.hasPath("gigachat.scope")) {
        config.getString("gigachat.scope")
    } else {
        "GIGACHAT_API_PERS"
    }

    log.info("GigaChat configuration loaded successfully")
    log.info("Client ID: ${clientId.take(10)}...")

    // Load OpenAI-compatible configuration (optional)
    val openAIBaseUrl = if (config.hasPath("openai.baseUrl")) {
        config.getString("openai.baseUrl")
    } else null

    val openAIApiKey = if (config.hasPath("openai.apiKey")) {
        config.getString("openai.apiKey")
    } else null

    val openAIModel = if (config.hasPath("openai.model")) {
        config.getString("openai.model")
    } else null

    val openAIMaxTokens = if (config.hasPath("openai.maxTokens")) {
        config.getInt("openai.maxTokens")
    } else null

    val openAITopP = if (config.hasPath("openai.topP")) {
        config.getDouble("openai.topP")
    } else null

    if (openAIBaseUrl != null && openAIApiKey != null) {
        log.info("OpenAI-compatible API configuration loaded successfully")
        log.info("OpenAI Base URL: $openAIBaseUrl")
        log.info("OpenAI Model: ${openAIModel ?: "gpt-3.5-turbo"}")
    }

    // Load Ollama configuration
    val ollamaBaseUrl = if (config.hasPath("ollama.baseUrl")) {
        config.getString("ollama.baseUrl")
    } else {
        "http://localhost:11434"
    }

    val ollamaModel = if (config.hasPath("ollama.model")) {
        config.getString("ollama.model")
    } else {
        "gemma3:1b"
    }

    val ollamaTimeout = if (config.hasPath("ollama.timeout")) {
        config.getLong("ollama.timeout")
    } else {
        120000L
    }

    val ollamaEnableSummarization = if (config.hasPath("ollama.enableSummarization")) {
        config.getBoolean("ollama.enableSummarization")
    } else {
        false
    }

    val ollamaEnableTools = if (config.hasPath("ollama.enableTools")) {
        config.getBoolean("ollama.enableTools")
    } else {
        true
    }

    val ollamaStreamFlushInterval = if (config.hasPath("ollama.streamFlushInterval")) {
        config.getInt("ollama.streamFlushInterval")
    } else {
        50
    }

    log.info("Ollama configuration loaded successfully")
    log.info("Ollama Base URL: $ollamaBaseUrl")
    log.info("Ollama Model: $ollamaModel")
    log.info("Ollama Enable Summarization: $ollamaEnableSummarization")
    log.info("Ollama Enable Tools: $ollamaEnableTools")

    install(Koin) {
        // slf4jLogger()
        printLogger(Level.DEBUG)
        modules(
            appModule(
                gigaChatBaseUrl, gigaChatAuthUrl, clientId, clientSecret, scope,
                openAIBaseUrl,
                openAIApiKey,
                openAIModel,
                openAIMaxTokens,
                openAITopP,
                ollamaBaseUrl,
                ollamaModel,
                ollamaTimeout,
                ollamaEnableSummarization,
                ollamaEnableTools,
                ollamaStreamFlushInterval
            )
        )
    }

    // Initialize database
    try {
        DatabaseFactory.init(config)
        log.info("Database initialized successfully")
    } catch (e: Exception) {
        log.error("Failed to initialize database. Continuing with in-memory storage only.", e)
    }

    // Load conversation history from database
    val chatService by inject<ChatService>()
    launch {
        try {
            chatService.loadAllHistory()
            log.info("Conversation history loaded from database")
        } catch (e: Exception) {
            log.error("Failed to load conversation history from database", e)
        }
    }

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
        anyHost()
    }

    // Configure routing
    routing {
        get("/") {
            call.respondText("GigaChat Chat Server is running")
        }

        chatRouting()
        modelsRouting()
        notificationRouting()
    }

    // Configure tool calling routing
    configureToolCallingRouting()

    // Initialize MCP connection on startup (optional - can also be done via /api/tools/connect)
    val mcpClientServiceList: List<IMcpClientService> = getKoin().getAll<IMcpClientService>()
    launch {
        mcpClientServiceList.forEach { mcpClientService ->
        try {
            log.info("Connecting to MCP server {} on startup...", mcpClientService.javaClass.simpleName)
            mcpClientService.connect()
            log.info("✓ MCP server {} connected successfully", mcpClientService.javaClass.simpleName)
        } catch (e: Exception) {
            log.warn(
                "Failed to connect to MCP server {} on startup: ${e.message}",
                mcpClientService.javaClass.simpleName
            )
            log.warn("You can manually connect using POST /api/tools/connect")
        }
        }
    }
    /*
        // Launch notification scheduler
        val notificationScheduler = getKoin().getOrNull<NotificationSchedulerService>()
        launch {
            if (notificationScheduler != null) {
                try {
                    log.info("Starting notification scheduler...")
                    notificationScheduler.start()
                } catch (e: Exception) {
                    log.error("Notification scheduler failed", e)
                }
            } else {
                log.warn("Notification scheduler not available (OpenRouter not configured)")
            }
        }*/
}