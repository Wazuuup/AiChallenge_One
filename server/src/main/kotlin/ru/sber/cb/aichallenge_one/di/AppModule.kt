package ru.sber.cb.aichallenge_one.di

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.dsl.bind
import org.koin.dsl.module
import ru.sber.cb.aichallenge_one.client.*
import ru.sber.cb.aichallenge_one.database.MessageRepository
import ru.sber.cb.aichallenge_one.domain.SummarizationConfig
import ru.sber.cb.aichallenge_one.service.*
import ru.sber.cb.aichallenge_one.service.mcp.IMcpClientService
import ru.sber.cb.aichallenge_one.service.mcp.impl.RAGMcpClientService
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

fun appModule(
    baseUrl: String,
    authUrl: String,
    clientId: String,
    clientSecret: String,
    scope: String,
    openAIBaseUrl: String? = null,
    openAIApiKey: String? = null,
    openAIModel: String? = null,
    openAIMaxTokens: Int? = null,
    openAITopP: Double? = null,
    ollamaBaseUrl: String = "http://localhost:11434",
    ollamaModel: String = "gemma3:1b",
    ollamaTimeout: Long = 120000L,
    ollamaEnableSummarization: Boolean = false,
    ollamaEnableTools: Boolean = true,
    ollamaStreamFlushInterval: Int = 50
) = module {
    // HttpClient for GigaChat with custom truststore
    single(qualifier = org.koin.core.qualifier.named("gigachat")) {
        val keystoreStream = this::class.java.classLoader.getResourceAsStream("truststore.jks")

        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(keystoreStream, "changeit".toCharArray())
            })
        }

        SSLContext.getInstance("TLS").apply {
            init(null, trustManagerFactory.trustManagers, SecureRandom())
        }

        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                    isLenient = true
                })
            }
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.INFO
            }
            engine {
                endpoint {
                    connectTimeout = 30000
                    requestTimeout = 60000
                }
                https {
                    this.trustManager = trustManagerFactory.trustManagers[0] as X509TrustManager

                }
            }
        }
    }

    // HttpClient for OpenAI/OpenRouter with default system certificates
    single(qualifier = org.koin.core.qualifier.named("openai")) {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                    isLenient = true
                    encodeDefaults = true  // Required for OpenRouter tool calling - includes fields with default values
                })
            }
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.BODY
            }
            engine {
                endpoint {
                    connectTimeout = 30000
                    requestTimeout = 60000
                }
            }
        }
    }

    // HttpClient for Ollama with default system certificates
    single(qualifier = org.koin.core.qualifier.named("ollama")) {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                    isLenient = true
                    encodeDefaults = true
                })
            }
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.BODY
            }
            engine {
                endpoint {
                    connectTimeout = 30000
                    requestTimeout = ollamaTimeout
                }
            }
        }
    }

    single {
        GigaChatApiClient(
            httpClient = get(org.koin.core.qualifier.named("gigachat")),
            baseUrl = baseUrl,
            authUrl = authUrl,
            clientId = clientId,
            clientSecret = clientSecret,
            scope = scope
        )
    }

    // OpenAI-compatible API client (optional)
    single {
        if (openAIBaseUrl != null && openAIApiKey != null) {
            OpenAIApiClient(
                httpClient = get(org.koin.core.qualifier.named("openai")),
                baseUrl = openAIBaseUrl,
                apiKey = openAIApiKey,
                model = openAIModel ?: "gpt-3.5-turbo",
                maxTokens = openAIMaxTokens,
                topP = openAITopP
            )
        } else {
            null
        }
    }

    // Ollama API client
    single {
        OllamaApiClient(
            httpClient = get(org.koin.core.qualifier.named("ollama")),
            baseUrl = ollamaBaseUrl,
            model = ollamaModel,
            timeout = ollamaTimeout
        )
    }

    // Ollama Client Adapter
    single {
        OllamaClientAdapter(
            ollamaApiClient = get()
        )
    }

    // OpenRouter Models Service (optional)
    single {
        if (openAIBaseUrl != null && openAIApiKey != null) {
            OpenRouterModelsService(
                httpClient = get(org.koin.core.qualifier.named("openai")),
                baseUrl = openAIBaseUrl,
                apiKey = openAIApiKey
            )
        } else {
            null
        }
    }

    // OpenRouter STT Client for audio transcription (optional)
    single {
        if (openAIBaseUrl != null && openAIApiKey != null) {
            OpenRouterSTTClient(
                httpClient = get(org.koin.core.qualifier.named("openai")),
                baseUrl = openAIBaseUrl,
                apiKey = openAIApiKey,
                model = "whisper-1"
            )
        } else {
            null
        }
    }

    // RAG Client - Calls RAG service for context retrieval
    single {
        RagClient(
            httpClient = get(org.koin.core.qualifier.named("openai"))
        )
    }

    // Summarization Configuration
    single {
        SummarizationConfig(
            threshold = 10,           // Summarize every 10 messages
            temperature = 0.3,        // Low temperature for consistent summaries
            summaryPrefix = "[Summary of previous conversation]"
        )
    }

    // Message Repository - Persistent storage for conversation history
    single { MessageRepository() }

    // Notification Repository - In-memory storage for notifications
    single { ru.sber.cb.aichallenge_one.repository.NotificationRepository() }

    // Summarization Service - Universal, provider-agnostic
    single { SummarizationService(config = get()) }

    // MCP Client Service - Connects to local mcp-server for tool calling

    /* single {
         val mcpVdsinaUrl = System.getenv("MCP_VDSINA_URL") ?: "http://localhost:8096"
         VdSinaMcpClientService(mcpServerUrl = mcpVdsinaUrl)
     } bind IMcpClientService::class
 */

    single {
        RAGMcpClientService()
    } bind IMcpClientService::class

    // Tool Adapter Service - Converts MCP tools to OpenRouter format
    single { ToolAdapterService() }

    // Tool Execution Service - Handles tool calling workflow
    single {
        val openAIClient = getOrNull<OpenAIApiClient>()
        if (openAIClient != null) {
            ToolExecutionService(
                mcpClientServiceList = getAll(),
                toolAdapterService = get(),
                openAIApiClient = openAIClient
            )
        } else {
            null
        }
    }

    // Notification Scheduler Service - Background job for notes summarization
    single {
        val toolExecService = getOrNull<ToolExecutionService>()
        if (toolExecService != null) {
            NotificationSchedulerService(
                toolExecutionService = toolExecService,
                notificationRepository = get()
            )
        } else {
            null
        }
    }

    // Chat Service - Refactored with Strategy pattern, persistence, and tool calling support
    single {
        ChatService(
            gigaChatApiClient = get(),
            openAIApiClient = getOrNull(),
            ollamaClientAdapter = getOrNull(),
            summarizationService = get(),
            messageRepository = get(),
            mcpClientServiceList = getAll(),
            toolAdapterService = get(),
            toolExecutionService = getOrNull(),
            ragClient = get(),
            ollamaEnableSummarization = ollamaEnableSummarization,
            ollamaEnableTools = ollamaEnableTools
        )
    }
}
