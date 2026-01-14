package ru.sber.cb.aichallenge_one.mcp_github_reviewer

import com.typesafe.config.ConfigFactory
import io.ktor.network.tls.certificates.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.sse.*
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore

const val GITHUB_REVIEWER_HTTP_PORT = 8095
const val GITHUB_REVIEWER_HTTPS_PORT = 8451

fun main() {
    // Загрузка конфига
    val config = ConfigFactory.load()

    // SSL Setup
    val sslPort = try {
        config.getInt("ktor.deployment.ssl_port")
    } catch (e: Exception) {
        GITHUB_REVIEWER_HTTPS_PORT
    }

    val keystoreFile = File("mcp/github-reviewer/src/main/resources/keystore.jks")
    val keyAlias = System.getenv("SSL_KEY_ALIAS") ?: "github-reviewer"
    val keyStorePassword = System.getenv("SSL_KEYSTORE_PASSWORD") ?: "changeit"
    val privateKeyPassword = System.getenv("SSL_KEY_PASSWORD") ?: "changeit"

    // Создание директории resources если не существует
    keystoreFile.parentFile?.mkdirs()

    // Генерация self-signed сертификата если файл не существует
    if (!keystoreFile.exists()) {
        println("SSL keystore not found. Generating self-signed certificate...")
        generateSelfSignedCertificate(
            file = keystoreFile,
            keyAlias = keyAlias,
            keyPassword = privateKeyPassword,
            jksPassword = keyStorePassword
        )
        println("✓ SSL keystore generated at: ${keystoreFile.absolutePath}")
    }

    println("Starting MCP GitHub Reviewer Server...")
    println("HTTP:  http://localhost:$GITHUB_REVIEWER_HTTP_PORT")
    println("HTTPS: https://localhost:$sslPort")

    // Загрузка KeyStore из файла
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
        FileInputStream(keystoreFile).use { fileInputStream ->
            load(fileInputStream, keyStorePassword.toCharArray())
        }
    }

    embeddedServer(
        factory = Netty,
        configure = {
            // HTTP connector
            connector {
                port = GITHUB_REVIEWER_HTTP_PORT
            }

            // HTTPS/SSL connector
            sslConnector(
                keyStore = keyStore,
                keyAlias = keyAlias,
                keyStorePassword = { keyStorePassword.toCharArray() },
                privateKeyPassword = { privateKeyPassword.toCharArray() }
            ) {
                port = sslPort
            }
        }
    ) {
        module()
    }.start(wait = true)
}

private fun generateSelfSignedCertificate(
    file: File,
    keyAlias: String,
    keyPassword: String,
    jksPassword: String
) {
    val keyStore = buildKeyStore {
        certificate(keyAlias) {
            password = keyPassword
            domains = listOf(
                "127.0.0.1",
                "localhost",
                "0.0.0.0"
            )
        }
    }

    FileOutputStream(file).use { outputStream ->
        keyStore.store(outputStream, jksPassword.toCharArray())
    }
}

fun Application.module() {
    // ContentNegotiation для JSON
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            isLenient = true
        })
    }

    // SSE - ОБЯЗАТЕЛЬНО для MCP!
    install(SSE)

    // Конфигурация MCP сервера
    configureGitHubMcpServer()
}
