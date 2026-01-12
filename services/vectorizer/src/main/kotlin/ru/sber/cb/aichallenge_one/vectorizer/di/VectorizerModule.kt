package ru.sber.cb.aichallenge_one.vectorizer.di

import com.typesafe.config.Config
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
import ru.sber.cb.aichallenge_one.vectorizer.service.repository.*

fun vectorizerModule(ollamaBaseUrl: String, config: Config) = module {
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

    // Repository Services
    single { GitIgnoreService() }
    single { SensitiveDataDetector() }
    single {
        // Load repository configuration
        val repoConfig = if (config.hasPath("repository")) config.getConfig("repository") else null

        val allowedPaths = repoConfig?.let {
            if (it.hasPath("allowedBasePaths") && it.getIsNull("allowedBasePaths").not()) {
                try {
                    it.getStringList("allowedBasePaths")
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
        } ?: emptyList()

        RepositoryValidator(
            config = RepositoryLimits(
                maxFiles = repoConfig?.getInt("maxFiles") ?: 10_000,
                maxFileSize = (repoConfig?.getInt("maxFileSizeMb") ?: 5) * 1024 * 1024L,
                maxTotalSize = (repoConfig?.getInt("maxTotalSizeMb") ?: 500) * 1024 * 1024L,
                maxDepth = repoConfig?.getInt("maxDepth") ?: 50,
                allowedBasePaths = allowedPaths
            )
        )
    }

    single {
        RepositoryVectorizationService(
            gitIgnoreService = get(),
            sensitiveDataDetector = get(),
            repositoryValidator = get(),
            chunkingService = get(),
            ollamaClient = get(),
            embeddingRepository = get(),
            fileProcessingService = get()
        )
    }
}
