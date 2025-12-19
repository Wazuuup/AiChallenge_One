package ru.sber.cb.aichallenge_one.mcp_client

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.sse.*
import io.ktor.serialization.kotlinx.json.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * MCP Client that calls the get_exchange_rate tool with USD currency code over HTTPS/SSL
 *
 * This client connects to the MCP server using HTTPS with a self-signed certificate.
 * For development purposes, it trusts all certificates (INSECURE - DO NOT USE IN PRODUCTION!)
 */
fun main() = runBlocking {
    println("╔═══════════════════════════════════════════════════════════╗")
    println("║   Currency Exchange Rate Client (USD → RUB) with SSL     ║")
    println("╚═══════════════════════════════════════════════════════════╝")
    println()

    // Create a trust manager that accepts all certificates (DEVELOPMENT ONLY!)
    val trustAllCertificates = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    // Create SSL context that trusts all certificates
    SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(trustAllCertificates), SecureRandom())
    }

    // Create HTTP client with SSL support for development (trusts self-signed certificates)
    val httpClient = HttpClient(CIO) {
        install(SSE)
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }

        engine {
            // Disable proxy
            proxy = null

            // Maximum number of connections
            maxConnectionsCount = 1000

            // Endpoint configuration
            endpoint {
                // Maximum number of requests per connection
                maxConnectionsPerRoute = 100
                // Maximum pipeline depth
                pipelineMaxSize = 20
                // Keep alive time
                keepAliveTime = 5000
                // Connect timeout
                connectTimeout = 5000
                // Connect retry attempts
                connectAttempts = 5
            }

            https {
                // Use custom SSL context
                trustManager = trustAllCertificates

                // Completely disable hostname verification by not setting serverName
                // Setting it to null disables SNI and hostname checks
                serverName = null

                // Use custom SSL context
                // Note: This is a workaround for Ktor CIO limitations
            }
        }

        // Don't throw exceptions on non-2xx responses
        expectSuccess = false
    }

    // Create MCP client using official SDK
    val mcpClient = Client(
        clientInfo = Implementation(
            name = "exchange-rate-ssl-client",
            version = "1.0.0"
        ),
        options = ClientOptions()
    )

    try {
        println("Connecting to MCP server at $MCP_SERVER_SSL_URL (HTTPS)...")
        println("Note: Using insecure SSL configuration (development only!)")
        println()

        // Create SSE transport to connect to server via HTTPS
        val transport = SseClientTransport(
            urlString = MCP_SERVER_SSL_URL,
            client = httpClient
        )

        // Connect to server
        mcpClient.connect(transport)

        println("✓ Successfully connected to MCP server via HTTPS")
        println()

        // Call get_exchange_rate tool with USD currency code
        println("Calling get_exchange_rate tool with currency_code: USD")
        println("═══════════════════════════════════════════════════════════")

        val result = mcpClient.callTool(
            name = "get_exchange_rate",
            arguments = mapOf("currency_code" to "USD")
        )

        println()
        if (result.isError == true) {
            println("✗ Error calling tool:")
            result.content.forEach { content ->
                println("  ${content}")
            }
        } else {
            println("✓ Tool call successful!")
            println()
            println("Result:")
            println("───────────────────────────────────────────────────────────")
            result.content.forEach { content ->
                println(content)
            }
            println("───────────────────────────────────────────────────────────")
        }

    } catch (e: Exception) {
        println("✗ Error calling MCP tool:")
        println("  ${e.message}")
        e.printStackTrace()
        println()
        println("Troubleshooting:")
        println("1. Make sure MCP server is running at $MCP_SERVER_SSL_URL")
        println("   Start with: .\\gradlew.bat :mcp-server:run")
        println()
        println("2. If hostname verification fails:")
        println("   - The client is configured to bypass hostname verification")
        println("   - If you still get errors, the server certificate might not be")
        println("     properly configured. Run: rebuild-with-new-cert.bat")
        println()
        println("3. Alternative: Use HTTP instead of HTTPS:")
        println("   .\\gradlew.bat :mcp-client:runExchangeRate")
    } finally {
        // Close connections
        try {
            mcpClient.close()
        } catch (e: Exception) {
            // Ignore errors during close
        }
        try {
            httpClient.close()
        } catch (e: Exception) {
            // Ignore errors during close
        }
    }
}
