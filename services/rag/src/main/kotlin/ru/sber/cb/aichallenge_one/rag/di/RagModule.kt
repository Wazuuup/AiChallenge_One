package ru.sber.cb.aichallenge_one.rag.di

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import ru.sber.cb.aichallenge_one.rag.repository.EmbeddingRepository
import ru.sber.cb.aichallenge_one.rag.service.RagService
import ru.sber.cb.aichallenge_one.rag.service.VectorizerClient

fun ragModule(vectorizerUrl: String) = module {
    // HTTP Client for vectorizer service
    single {
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
        }
    }

    // Repository
    single { EmbeddingRepository() }

    // Services
    single { VectorizerClient(get(), vectorizerUrl) }
    single {
        RagService(
            embeddingRepository = get(),
            vectorizerClient = get()
        )
    }
}
