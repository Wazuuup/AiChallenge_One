package ru.sber.cb.aichallenge_one.di

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import ru.sber.cb.aichallenge_one.client.GigaChatApiClient
import ru.sber.cb.aichallenge_one.service.ChatService
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
    scope: String
) = module {
    single {
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

    single {
        GigaChatApiClient(
            httpClient = get(),
            baseUrl = baseUrl,
            authUrl = authUrl,
            clientId = clientId,
            clientSecret = clientSecret,
            scope = scope
        )
    }

    single { ChatService(get()) }
}
