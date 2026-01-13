package ru.sber.cb.aichallenge_one.mcp_git

import com.typesafe.config.ConfigFactory
import io.ktor.network.tls.certificates.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.sse.*
import kotlinx.serialization.json.Json
import ru.sber.cb.aichallenge_one.mcp_git.service.GitService
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore

fun main() {
    // Load configuration from application.conf
    val config = ConfigFactory.load()

    // Get repository path from environment or config (fallback to current directory)
    val repositoryPath = System.getenv("GIT_REPO_PATH")
        ?: try {
            config.getString("git.repository_path")
        } catch (e: Exception) {
            System.getProperty("user.dir")
        }

    println("Git repository path: $repositoryPath")

    // Validate repository exists
    val repoDir = File(repositoryPath)
    if (!repoDir.exists()) {
        error("Repository directory does not exist: $repositoryPath")
    }
    if (!File(repoDir, ".git").exists()) {
        error("Not a Git repository: $repositoryPath")
    }

    // Read SSL port from config
    val sslPort = try {
        config.getInt("ktor.deployment.ssl_port")
    } catch (e: Exception) {
        MCP_GIT_HTTPS_PORT
    }

    val keystoreFile = File("mcp/git/src/main/resources/keystore.jks")
    val keyAlias = System.getenv("SSL_KEY_ALIAS") ?: "mcpgitserver"
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

    println("Starting MCP Git Server...")
    println("HTTP:  http://localhost:$MCP_GIT_HTTP_PORT")
    println("HTTPS: https://localhost:$sslPort")

    // Load the KeyStore
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
        FileInputStream(keystoreFile).use { fileInputStream ->
            load(fileInputStream, keyStorePassword.toCharArray())
        }
    }

    // Initialize GitService
    val gitService = GitService(repositoryPath)

    embeddedServer(
        factory = Netty,
        configure = {
            // HTTP connector
            connector {
                port = MCP_GIT_HTTP_PORT
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
        }
    ) {
        module(gitService)
    }.start(wait = true)
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

fun Application.module(gitService: GitService) {
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

    // Configure MCP Git Server (includes all routing)
    configureMcpGitServer(gitService)

    // Shutdown hook to close GitService
    environment.monitor.subscribe(ApplicationStopped) {
        gitService.close()
    }
}
