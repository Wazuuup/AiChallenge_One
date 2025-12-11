package ru.sber.cb.aichallenge_one.di

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import ru.sber.cb.aichallenge_one.client.GigaChatApiClient
import ru.sber.cb.aichallenge_one.client.OpenAIApiClient
import ru.sber.cb.aichallenge_one.service.ChatService
import ru.sber.cb.aichallenge_one.service.OpenRouterModelsService
import ru.sber.cb.aichallenge_one.service.SummarizationService
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
    openAITopP: Double? = null
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

    // Summarization Service (supports both GigaChat and OpenRouter)
    single { SummarizationService(gigaChatApiClient = get(), openAIApiClient = get()) }

    // Chat Service (depends on GigaChatApiClient, OpenAIApiClient, and SummarizationService)
    single { ChatService(gigaChatApiClient = get(), openAIApiClient = get(), summarizationService = get()) }
}
