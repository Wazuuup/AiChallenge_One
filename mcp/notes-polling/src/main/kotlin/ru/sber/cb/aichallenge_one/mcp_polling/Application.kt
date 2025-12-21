package ru.sber.cb.aichallenge_one.mcp_polling

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

const val MCP_POLLING_PORT = 8089  // Note: Changed from 8088 due to port conflict
const val MCP_POLLING_SSL_PORT = 8448

fun main() {
    // Load configuration from application.conf
    val config = ConfigFactory.load()

    // Read SSL port from config, fallback to constant if not specified
    val sslPort = try {
        config.getInt("ktor.deployment.ssl_port")
    } catch (e: Exception) {
        MCP_POLLING_SSL_PORT
    }

    val keystoreFile = File("src/main/resources/keystore.jks")
    val keyAlias = System.getenv("SSL_KEY_ALIAS") ?: "mcppolling"
    val keyStorePassword = System.getenv("SSL_KEYSTORE_PASSWORD") ?: "changeit"
    val privateKeyPassword = System.getenv("SSL_KEY_PASSWORD") ?: "changeit"

    // Ensure resources directory exists
    keystoreFile.parentFile?.mkdirs()

    // Generate keystore if it doesn't exist
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

    println("Starting MCP Notes Polling Server...")
    println("HTTP:  http://localhost:$MCP_POLLING_PORT")
    println("HTTPS: https://localhost:$sslPort")

    // Load the KeyStore
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
                port = MCP_POLLING_PORT
            }

            // HTTPS/SSL connector (always enabled)
            sslConnector(
                keyStore = keyStore,
                keyAlias = keyAlias,
                keyStorePassword = { keyStorePassword.toCharArray() },
                privateKeyPassword = { privateKeyPassword.toCharArray() }
            ) {
                port = sslPort
            }
        },
        module = Application::module
    ).start(wait = true)
}

/**
 * Generates a self-signed certificate and stores it in a JKS keystore file.
 */
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

    // Save the keystore to file
    FileOutputStream(file).use { outputStream ->
        keyStore.store(outputStream, jksPassword.toCharArray())
    }
}

fun Application.module() {
    // Install ContentNegotiation with JSON
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            isLenient = true
        })
    }

    // Install SSE (Server-Sent Events) - required for MCP
    install(SSE)

    // Configure MCP Server (includes all routing)
    configureMcpPollingServer()
}
