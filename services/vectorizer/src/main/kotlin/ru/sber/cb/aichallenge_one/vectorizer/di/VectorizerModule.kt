package ru.sber.cb.aichallenge_one.vectorizer.di

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import ru.sber.cb.aichallenge_one.vectorizer.repository.EmbeddingRepository
import ru.sber.cb.aichallenge_one.vectorizer.service.ChunkingService
import ru.sber.cb.aichallenge_one.vectorizer.service.FileProcessingService
import ru.sber.cb.aichallenge_one.vectorizer.service.OllamaEmbeddingClient
import ru.sber.cb.aichallenge_one.vectorizer.service.VectorizerService

fun vectorizerModule(ollamaBaseUrl: String) = module {
    // HTTP Client for Ollama
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

    // Services
    single { FileProcessingService() }
    single { ChunkingService() }
    single { OllamaEmbeddingClient(get(), ollamaBaseUrl) }
    single { EmbeddingRepository() }

    single {
        VectorizerService(
            fileProcessingService = get(),
            chunkingService = get(),
            ollamaClient = get(),
            embeddingRepository = get()
        )
    }
}
