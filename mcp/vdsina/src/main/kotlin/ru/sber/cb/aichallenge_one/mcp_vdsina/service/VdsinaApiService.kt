package ru.sber.cb.aichallenge_one.mcp_vdsina.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import ru.sber.cb.aichallenge_one.mcp_vdsina.model.*
import java.io.BufferedReader
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
    private val minRamGb: Int,
    private val deployScriptPath: String
) {
    private val logger = LoggerFactory.getLogger(VdsinaApiService::class.java)

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
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

    private suspend inline fun <reified T> delete(endpoint: String): T {
        val response = httpClient.delete("$baseUrl$endpoint") {
            header("Authorization", "Bearer $apiToken")
        }
        return response.body()
    }

    suspend fun listDatacenters(): String {
        logger.info("Fetching datacenters...")
        val response: DatacentersResponse = get("/datacenter")
        return Json.encodeToString(DatacentersResponse.serializer(), response)
    }

    suspend fun listServerPlans(groupId: Int): String {
        logger.info("Fetching server plans for group $groupId...")
        val response: ServerPlansResponse = get("/server-plan/$groupId")
        return Json.encodeToString(ServerPlansResponse.serializer(), response)
    }

    suspend fun listServerGroups(): String {
        logger.info("Fetching server groups...")
        val response: ServerGroupsResponse = get("/server-group")
        return Json.encodeToString(ServerGroupsResponse.serializer(), response)
    }

    suspend fun listTemplates(): String {
        logger.info("Fetching templates...")
        val response: TemplatesResponse = get("/template")
        return Json.encodeToString(TemplatesResponse.serializer(), response)
    }

    suspend fun listSshKeys(): String {
        logger.info("Fetching SSH keys...")
        val response: SshKeysResponse = get("/ssh-key")
        return Json.encodeToString(SshKeysResponse.serializer(), response)
    }

    suspend fun createSshKey(name: String, data: String): String {
        logger.info("Creating SSH key: $name")
        val request = CreateSshKeyRequest(name, data)
        val response: CreateSshKeyResponse = post("/ssh-key", request)
        return Json.encodeToString(CreateSshKeyResponse.serializer(), response)
    }

    suspend fun listServers(): String {
        logger.info("Fetching servers...")
        val response: ServersResponse = get("/server")
        return Json.encodeToString(ServersResponse.serializer(), response)
    }

    suspend fun getServerStatus(serverId: Int): String {
        logger.info("Fetching status for server $serverId...")
        val response: ServerInfo = get("/server/$serverId")
        return Json.encodeToString(ServerInfo.serializer(), response)
    }

    suspend fun createServer(name: String?): String {
        logger.info("Creating server with minimum configuration...")

        // Step 1: Get server groups
        val groups: ServerGroupsResponse = get("/server-group")
        val activeGroups = groups.groups.filter { it.active }
        if (activeGroups.isEmpty()) {
            throw IllegalStateException("No active server groups found")
        }

        // Step 2: Find cheapest plan with minRamGb
        var cheapestPlan: ServerPlan? = null
        for (group in activeGroups) {
            val plansResponse: ServerPlansResponse = get("/server-plan/${group.id}")
            val eligiblePlans = plansResponse.plans.filter {
                it.active && (it.enable == null || it.enable) && it.ramGb >= minRamGb
            }.sortedBy { it.cost }

            if (eligiblePlans.isNotEmpty() && (cheapestPlan == null || eligiblePlans[0].cost < cheapestPlan.cost)) {
                cheapestPlan = eligiblePlans[0]
            }
        }

        if (cheapestPlan == null) {
            throw IllegalStateException("No eligible server plans found (minRamGb=$minRamGb)")
        }

        logger.info("Selected plan: ${cheapestPlan.name} (${cheapestPlan.cost} ${cheapestPlan.period})")

        // Step 3: Get first active datacenter
        val datacenters: DatacentersResponse = get("/datacenter")
        val activeDatacenter = datacenters.datacenters.firstOrNull { it.active }
            ?: throw IllegalStateException("No active datacenters found")

        logger.info("Selected datacenter: ${activeDatacenter.name}")

        // Step 4: Get Ubuntu 24.04 template with SSH key support
        val templates: TemplatesResponse = get("/template")
        val ubuntuTemplate = templates.templates.firstOrNull {
            it.active && it.sshKeySupported && it.name.contains("Ubuntu 24.04", ignoreCase = true)
        } ?: throw IllegalStateException("Ubuntu 24.04 template with SSH key support not found")

        logger.info("Selected template: ${ubuntuTemplate.name}")

        // Step 5: Create server
        val serverName = name ?: "AiChallenge-${SimpleDateFormat("yyyyMMdd-HHmmss").format(Date())}"
        val request = CreateServerRequest(
            name = serverName,
            datacenterId = activeDatacenter.id,
            planId = cheapestPlan.id,
            templateId = ubuntuTemplate.id,
            sshKeyId = sshKeyId
        )

        val response: CreateServerResponse = post("/server", request)
        logger.info("Server creation started: ${response.serverId}")

        return Json.encodeToString(CreateServerResponse.serializer(), response)
    }

    suspend fun deleteServer(serverId: Int): String {
        logger.info("Deleting server $serverId...")
        val response: DeleteServerResponse = delete("/server/$serverId")
        return Json.encodeToString(DeleteServerResponse.serializer(), response)
    }

    suspend fun deployApp(): String {
        logger.info("Starting deployment process...")

        // Step 1: Get list of servers
        val serversResponse: ServersResponse = get("/server")
        if (serversResponse.servers.isEmpty()) {
            throw IllegalStateException("No servers found in account")
        }

        // Step 2: Find most recent active server
        val activeServers = serversResponse.servers
            .filter { it.status == "active" }
            .sortedByDescending { it.created }

        if (activeServers.isEmpty()) {
            val latestServer = serversResponse.servers.first()
            throw IllegalStateException("No active servers found. Latest server status: ${latestServer.status}")
        }

        val targetServer = activeServers.first()
        val serverIp = targetServer.ip ?: throw IllegalStateException("Server has no IP address")

        logger.info("Deploying to server ${targetServer.id} (${targetServer.name}) at $serverIp")

        // Step 3: Execute PowerShell deploy script
        val scriptPath = System.getProperty("user.dir") + "\\$deployScriptPath"
        val processBuilder = ProcessBuilder(
            "powershell.exe",
            "-ExecutionPolicy", "Bypass",
            "-File", scriptPath,
            "-ServerIP", serverIp
        )

        processBuilder.redirectErrorStream(true)
        val process = processBuilder.start()

        // Read output
        val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            reader.readText()
        }

        val exitCode = process.waitFor()
        logger.info("Deploy script exit code: $exitCode")
        logger.debug("Deploy script output:\n$output")

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

        return Json.encodeToString(DeployResult.serializer(), result)
    }

    fun close() {
        httpClient.close()
    }
}
