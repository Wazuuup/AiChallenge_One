package ru.sber.cb.aichallenge_one.mcp_vdsina

import com.typesafe.config.ConfigFactory
import io.ktor.network.tls.certificates.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.sse.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import ru.sber.cb.aichallenge_one.mcp_vdsina.service.VdsinaApiService
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore

fun main() {
    // Load configuration with application-dev.conf fallback
    val config = loadConfig()

    // Read SSL port from config
    val sslPort = try {
        config.getInt("ktor.deployment.ssl_port")
    } catch (e: Exception) {
        MCP_VDSINA_HTTPS_PORT
    }

    val keystoreFile = File("mcp/vdsina/src/main/resources/keystore.jks")
    val keyAlias = System.getenv("SSL_KEY_ALIAS") ?: "vdsina-mcp"
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

    println("Starting MCP VDSina Server...")
    println("HTTP:  http://localhost:$MCP_VDSINA_HTTP_PORT")
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
                port = MCP_VDSINA_HTTP_PORT
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
        module()
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

/**
 * Loads configuration with application-dev.conf fallback.
 * Priority: application-dev.conf > application.conf > environment variables
 */
private fun loadConfig(): com.typesafe.config.Config {
    val devConfigFile = File("mcp/vdsina/src/main/resources/application-dev.conf")
    return if (devConfigFile.exists()) {
        println("Loading configuration from application-dev.conf")
        ConfigFactory.load("application-dev")
    } else {
        println("Loading configuration from application.conf (application-dev.conf not found)")
        ConfigFactory.load()
    }
}

/**
 * Safe config string getter with fallback to environment variable
 */
private fun com.typesafe.config.Config.getStringOrEnv(path: String, envVar: String): String? {
    return try {
        if (hasPath(path)) getString(path).takeIf { it.isNotBlank() } else null
    } catch (e: Exception) {
        null
    } ?: System.getenv(envVar)
}

/**
 * Safe config int getter with fallback to environment variable
 */
private fun com.typesafe.config.Config.getIntOrEnv(path: String, envVar: String): Int? {
    return try {
        if (hasPath(path)) getInt(path) else null
    } catch (e: Exception) {
        null
    } ?: System.getenv(envVar)?.toIntOrNull()
}

fun Application.module() {
    // Load configuration with dev fallback
    val config = loadConfig()

    // Install Koin DI
    install(Koin) {
        modules(vdsinaModule(config))
    }

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

    // Configure MCP VDSina Server (includes all routing)
    configureMcpVdsinaServer()
}

/**
 * Koin DI module for VDSina MCP server
 */
fun vdsinaModule(config: com.typesafe.config.Config) = module {
    single {
        val apiToken = config.getStringOrEnv("vdsina.apiToken", "VDSINA_API_TOKEN")
            ?: throw IllegalStateException("VDSINA_API_TOKEN not configured (set in application-dev.conf or environment variable)")
        val sshKeyId = config.getIntOrEnv("vdsina.sshKeyId", "VDSINA_SSH_KEY_ID")
        val datacenterId = config.getInt("vdsina.datacenterId")
        val planId = config.getInt("vdsina.planId")
        val templateId = config.getInt("vdsina.templateId")
        val sudoPass = config.getStringOrEnv("vdsina.sudoPass", "VDSINA_SUDO_PASS")
            ?: throw IllegalStateException("VDSINA_SUDO_PASS not configured (set in application-dev.conf or environment variable)")
        val sshKeyPath = config.getStringOrEnv("deploy.sshKeyPath", "SSH_KEY_PATH")

        println("VDSina API token: ${apiToken.take(8)}...${apiToken.takeLast(4)} (length: ${apiToken.length})")
        println("VDSina config: sshKeyId=$sshKeyId, datacenterId=$datacenterId, planId=$planId, templateId=$templateId")
        println("Deploy config: sshKeyPath=$sshKeyPath")

        VdsinaApiService(
            baseUrl = config.getString("vdsina.baseUrl"),
            apiToken = apiToken,
            sshKeyId = sshKeyId,
            datacenterId = datacenterId,
            planId = planId,
            templateId = templateId,
            newPassword = sudoPass,
            deployScriptPath = config.getString("deploy.scriptPath"),
            sshKeyPath = sshKeyPath
        )
    }
}
