package ru.sber.cb.aichallenge_one.mcp_vdsina.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.mcp_vdsina.model.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

/**
 * Service for interacting with VDSina API
 */
class VdsinaApiService(
    private val baseUrl: String,
    private val apiToken: String,
    private val sshKeyId: Int?,
    private val datacenterId: Int,
    private val planId: Int,
    private val templateId: Int,
    private val newPassword: String,
    private val deployScriptPath: String,
    private val sshKeyPath: String?
) {
    private val logger = LoggerFactory.getLogger(VdsinaApiService::class.java)

    private val jsonFormat = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        isLenient = true
    }

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(jsonFormat)
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.BODY
        }
    }

    private suspend inline fun <reified T> get(endpoint: String): T {
        val response = httpClient.get("$baseUrl$endpoint") {
            header("Authorization", "Bearer $apiToken")
        }
        return response.body()
    }

    private suspend inline fun <reified T, reified R> post(endpoint: String, body: T): R {
        val response = httpClient.post("$baseUrl$endpoint") {
            header("Authorization", "Bearer $apiToken")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        return response.body()
    }

    private suspend inline fun <reified T, reified R> put(endpoint: String, body: T): R {
        val response = httpClient.put("$baseUrl$endpoint") {
            header("Authorization", "Bearer $apiToken")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        return response.body()
    }

    private suspend inline fun <reified T> delete(endpoint: String): T {
        val response = httpClient.delete("$baseUrl$endpoint") {
            header("Authorization", "Bearer $apiToken")
        }
        return response.body()
    }

    suspend fun listDatacenters(): String {
        logger.info("Fetching datacenters...")
        val response: DatacentersResponse = get("/datacenter")
        response.requireData() // Validate response before returning
        return jsonFormat.encodeToString(response)
    }

    suspend fun listServerPlans(groupId: Int): String {
        logger.info("Fetching server plans for group $groupId...")
        val response: ServerPlansResponse = get("/server-plan/$groupId")
        response.requireData() // Validate response before returning
        return jsonFormat.encodeToString(response)
    }

    suspend fun listServerGroups(): String {
        logger.info("Fetching server groups...")
        val response: ServerGroupsResponse = get("/server-group")
        response.requireData() // Validate response before returning
        return jsonFormat.encodeToString(response)
    }

    suspend fun listTemplates(): String {
        logger.info("Fetching templates...")
        val response: TemplatesResponse = get("/template")
        response.requireData() // Validate response before returning
        return jsonFormat.encodeToString(response)
    }

    suspend fun listSshKeys(): String {
        logger.info("Fetching SSH keys...")
        val response: SshKeysResponse = get("/ssh-key")
        response.requireData() // Validate response before returning
        return jsonFormat.encodeToString(response)
    }

    suspend fun createSshKey(name: String, keyData: String): String {
        logger.info("Creating SSH key: $name")
        val request = CreateSshKeyRequest(name, keyData)
        val response: CreateSshKeyResponse = post("/ssh-key", request)
        return jsonFormat.encodeToString(CreateSshKeyResponse.serializer(), response)
    }

    suspend fun listServers(): String {
        logger.info("Fetching servers...")
        val response: ServersResponse = get("/server")
        response.requireData() // Validate response before returning
        return jsonFormat.encodeToString(response)
    }

    suspend fun getServerStatus(serverId: Int): String {
        logger.info("Fetching status for server $serverId...")
        val response: ServerStatusResponse = get("/server/$serverId")
        response.requireData() // Validate response before returning
        return jsonFormat.encodeToString(response)
    }

    /**
     * Waits for server to become active.
     * Returns status message for LLM to act upon.
     */
    suspend fun waitForServerActive(serverId: Int): WaitForServerResult {
        logger.info("Checking if server $serverId is active...")
        val response: ServerStatusResponse = get("/server/$serverId")
        val serverData = response.requireData()

        val status = serverData.status
        logger.info("Server $serverId status: $status")

        return when (status) {
            "active" -> {
                val ip = serverData.getIpAddress() ?: "IP not assigned yet"
                WaitForServerResult(
                    ready = true,
                    message = "Сервер активен и готов к работе. IP: $ip",
                    serverStatus = status,
                    serverIp = ip
                )
            }

            "deleted" -> {
                WaitForServerResult(
                    ready = false,
                    message = "Сервер удален, прерви выполнение и выведи это пользователю.",
                    serverStatus = status,
                    serverIp = null,
                    shouldAbort = true
                )
            }

            else -> {
                // Wait 30 seconds before returning
                logger.info("Server not active yet (status: $status), waiting 30 seconds...")
                kotlinx.coroutines.delay(30_000)
                WaitForServerResult(
                    ready = false,
                    message = "Сервер еще недоступен (статус: $status), ОБЯЗАТЕЛЬНО ПОВТОРИ ЗАПРОС check_server_status_and_wait СРАЗУ ПОСЛЕ ПОЛУЧЕНИЯ ДАННОГО СООБЩЕНИЯ",
                    serverStatus = status,
                    serverIp = null
                )
            }
        }
    }

    suspend fun createServer(name: String?): String {
        logger.info("Creating server with fixed configuration...")
        logger.info("  datacenter: $datacenterId, plan: $planId, template: $templateId, sshKey: $sshKeyId")

        val serverName = name ?: "AiChallenge-${SimpleDateFormat("yyyyMMdd-HHmmss").format(Date())}"
        val request = CreateServerRequest(
            name = serverName,
            datacenterId = datacenterId,
            planId = planId,
            templateId = templateId,
            sshKeyId = sshKeyId,
            backup = 0
        )

        val response: CreateServerResponse = post("/server", request)

        if (!response.isSuccess()) {
            val errorMsg = response.description ?: response.statusMsg
            throw IllegalStateException("Server creation failed: $errorMsg (status_code: ${response.statusCode})")
        }

        val serverId = response.data?.id
        logger.info("Server creation response: status=${response.status}, serverId=$serverId")

        return jsonFormat.encodeToString(CreateServerResponse.serializer(), response)
    }

    suspend fun deleteServer(serverId: Int): String {
        logger.info("Deleting server $serverId...")
        val response: DeleteServerResponse = delete("/server/$serverId")
        return jsonFormat.encodeToString(DeleteServerResponse.serializer(), response)
    }

    suspend fun getServerPassword(serverId: Int): String {
        logger.info("Getting root password for server $serverId...")
        val response: ServerPasswordResponse = get("/server/$serverId/password")

        if (!response.isSuccess()) {
            val errorMsg = response.description ?: response.statusMsg
            throw IllegalStateException("Failed to get password: $errorMsg (status_code: ${response.statusCode})")
        }

        response.data?.password
        logger.info("Password retrieved for server $serverId")

        return jsonFormat.encodeToString(ServerPasswordResponse.serializer(), response)
    }

    suspend fun changeServerPassword(serverId: Int): String {
        logger.info("Changing root password for server $serverId to configured password...")
        val request = ChangePasswordRequest(password = newPassword)
        val response: ChangePasswordResponse = put("/server.password/$serverId", request)

        if (!response.isSuccess()) {
            val validationErrors = response.getValidationErrors()
            val errorMsg = validationErrors ?: response.description ?: response.statusMsg
            throw IllegalStateException("Failed to change password: $errorMsg (status_code: ${response.statusCode})")
        }

        logger.info("Password changed successfully for server $serverId")

        return jsonFormat.encodeToString(ChangePasswordResponse.serializer(), response)
    }

    suspend fun deployApp(): String {
        logger.info("Starting deployment process...")

        // Step 1: Get list of servers
        val serversResponse: ServersResponse = get("/server")
        val servers = serversResponse.requireData()
        if (servers.isEmpty()) {
            throw IllegalStateException("No servers found in account")
        }

        // Step 2: Find most recent active server
        val activeServers = servers
            .filter { it.status == "active" }
            .sortedByDescending { it.created }

        if (activeServers.isEmpty()) {
            val latestServer = servers.first()
            throw IllegalStateException("No active servers found. Latest server status: ${latestServer.status}")
        }

        val targetServer = activeServers.first()
        val serverIp = targetServer.getIpAddress() ?: throw IllegalStateException("Server has no IP address")

        logger.info("Deploying to server ${targetServer.id} (${targetServer.name}) at $serverIp")

        // Step 3: Execute PowerShell deploy script with password
        // Navigate to project root from mcp/vdsina module
        val projectRoot = File(System.getProperty("user.dir")).parentFile.parentFile
        val scriptPath = File(projectRoot, deployScriptPath).absolutePath

        logger.info("Running deploy script...")
        val processBuilder = ProcessBuilder(
            "powershell.exe",
            "-ExecutionPolicy", "Bypass",
            "-File", scriptPath,
            "-ServerIP", serverIp,
            "-Password", newPassword
        )

        processBuilder.redirectErrorStream(true)
        val process = processBuilder.start()

        // Read output in real-time and log it
        val output = StringBuilder()
        BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
                logger.info("[Deploy] $line")
            }
        }

        val exitCode = process.waitFor()
        logger.info("Deploy script exit code: $exitCode")

        if (exitCode != 0) {
            throw RuntimeException("Deployment script failed with exit code $exitCode:\n$output")
        }

        // Step 4: Return result
        val result = DeployResult(
            success = true,
            serverIp = serverIp,
            message = "Deployment completed successfully",
            frontendUrl = "http://$serverIp",
            apiUrl = "http://$serverIp/api"
        )

        return jsonFormat.encodeToString(DeployResult.serializer(), result)
    }

    fun close() {
        httpClient.close()
    }
}
