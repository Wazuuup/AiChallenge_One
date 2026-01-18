package ru.sber.cb.aichallenge_one.mcp_vdsina.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * VDSina API response models
 *
 * VDSina API wraps all responses in a standard format:
 * {
 *   "status": "ok",
 *   "status_msg": "",
 *   "data": <actual data>
 * }
 */

/**
 * Generic VDSina API response wrapper
 * Note: data is nullable because error responses return "data": null
 */
@Serializable
data class VdsinaResponse<T>(
    val status: String,
    @SerialName("status_code")
    val statusCode: Int? = null,
    @SerialName("status_msg")
    val statusMsg: String = "",
    val description: String? = null,
    val data: T? = null
) {
    fun isSuccess(): Boolean = status == "ok"

    fun requireData(): T = data ?: throw IllegalStateException(
        "API error: ${description ?: statusMsg} (status_code: $statusCode)"
    )
}

@Serializable
data class Datacenter(
    val id: Int,
    val name: String,
    val country: String? = null,
    val active: Boolean
)

typealias DatacentersResponse = VdsinaResponse<List<Datacenter>>

@Serializable
data class ServerPlan(
    val id: Int,
    val name: String,
    val cost: Double? = null,
    val period: String? = null,
    val cpu: Int? = null,
    @SerialName("ram_mb")
    val ramMb: Int? = null,
    @SerialName("ram_gb")
    val ramGb: Int? = null,
    @SerialName("disk_gb")
    val diskGb: Int? = null,
    @SerialName("hdd_gb")
    val hddGb: Int? = null,
    @SerialName("ssd_gb")
    val ssdGb: Int? = null,
    val active: Boolean = true,
    val enable: Boolean? = null
) {
    /**
     * Get RAM in GB, converting from MB if needed
     */
    fun getRamGbValue(): Int {
        return ramGb ?: (ramMb?.let { it / 1024 } ?: 0)
    }

    /**
     * Get disk size in GB (from any available disk field)
     */
    fun getDiskGbValue(): Int {
        return diskGb ?: ssdGb ?: hddGb ?: 0
    }
}

typealias ServerPlansResponse = VdsinaResponse<List<ServerPlan>>

@Serializable
data class ServerGroup(
    val id: Int,
    val name: String,
    val active: Boolean,
    val description: String? = null
)

typealias ServerGroupsResponse = VdsinaResponse<List<ServerGroup>>

@Serializable
data class Template(
    val id: Int,
    val name: String,
    val active: Boolean,
    val image: String? = null,
    @SerialName("ssh_key_supported")
    val sshKeySupported: Boolean? = null
) {
    fun isSshKeySupported(): Boolean = sshKeySupported ?: false
}

typealias TemplatesResponse = VdsinaResponse<List<Template>>

@Serializable
data class SshKey(
    val id: Int,
    val name: String
)

typealias SshKeysResponse = VdsinaResponse<List<SshKey>>

@Serializable
data class CreateSshKeyRequest(
    val name: String,
    val data: String
)

@Serializable
data class CreateSshKeyResponse(
    val status: String,
    @SerialName("status_msg")
    val statusMsg: String = "",
    val data: SshKeyData? = null
)

@Serializable
data class SshKeyData(
    val id: Int
)

@Serializable
data class ServerInfo(
    val id: Int,
    val name: String? = null,
    val status: String,
    @SerialName("status_text")
    val statusText: String? = null,
    val created: String? = null,
    val updated: String? = null,
    val end: String? = null,
    val autoprolong: Boolean? = null,
    val autorun: Boolean? = null,
    val ip: ServerIpInfo? = null,
    @SerialName("ip_local")
    val ipLocal: ServerLocalIpInfo? = null,
    val host: String? = null,
    val data: ServerTariffData? = null,
    @SerialName("server-plan")
    val serverPlan: PlanInfo? = null,
    @SerialName("server-group")
    val serverGroup: ServerGroupInfo? = null,
    val template: TemplateInfo? = null,
    val datacenter: DatacenterInfo? = null,
    @SerialName("ssh-key")
    val sshKey: SshKeyInfo? = null,
    val can: ServerCapabilities? = null,
    val bandwidth: ServerBandwidth? = null
) {
    /**
     * Get the IP address as string
     */
    fun getIpAddress(): String? = ip?.ip
}

@Serializable
data class ServerIpInfo(
    val id: Int? = null,
    val ip: String? = null,
    val type: String? = null
)

@Serializable
data class ServerLocalIpInfo(
    val ip: String? = null,
    val netmask: String? = null,
    val mac: String? = null
)

@Serializable
data class ServerTariffData(
    val cpu: TariffValue? = null,
    val ram: TariffValueBytes? = null,
    val disk: TariffValueBytes? = null,
    val traff: TariffValueBytes? = null,
    val gpu: TariffValue? = null
)

@Serializable
data class TariffValue(
    val value: Long? = null,
    @SerialName("for")
    val forUnit: String? = null
)

@Serializable
data class TariffValueBytes(
    val value: Long? = null,
    val bytes: Long? = null,
    @SerialName("for")
    val forUnit: String? = null
)

@Serializable
data class ServerGroupInfo(
    val id: Int,
    val name: String
)

@Serializable
data class SshKeyInfo(
    val id: Int,
    val name: String
)

@Serializable
data class ServerCapabilities(
    val reboot: Boolean? = null,
    val update: Boolean? = null,
    val delete: Boolean? = null,
    val prolong: Boolean? = null,
    val backup: Boolean? = null,
    @SerialName("ip_local")
    val ipLocal: Boolean? = null
)

@Serializable
data class ServerBandwidth(
    @SerialName("current_month")
    val currentMonth: Long? = null,
    @SerialName("past_month")
    val pastMonth: Long? = null
)

@Serializable
data class DatacenterInfo(
    val id: Int,
    val name: String,
    val country: String? = null
)

@Serializable
data class PlanInfo(
    val id: Int,
    val name: String
)

@Serializable
data class TemplateInfo(
    val id: Int,
    val name: String,
    val image: String? = null
)

typealias ServersResponse = VdsinaResponse<List<ServerInfo>>
typealias ServerStatusResponse = VdsinaResponse<ServerInfo>

@Serializable
data class CreateServerRequest(
    val name: String = "aichallenge",
    @SerialName("datacenter")
    val datacenterId: Int,
    @SerialName("server-plan")
    val planId: Int,
    @SerialName("template")
    val templateId: Int,
    @SerialName("ssh_key")
    val sshKeyId: Int? = null,
    val backup: Int = 0,
    val autoprolong: Int = 1
)

@Serializable
data class CreateServerResponse(
    val status: String,
    @SerialName("status_code")
    val statusCode: Int? = null,
    @SerialName("status_msg")
    val statusMsg: String = "",
    val description: String? = null,
    val data: CreateServerData? = null
) {
    fun isSuccess(): Boolean = status == "ok"
}

@Serializable
data class CreateServerData(
    val id: Int? = null,
    val name: String? = null,
    val status: String? = null
)

@Serializable
data class DeleteServerResponse(
    val status: String,
    @SerialName("status_msg")
    val statusMsg: String = "",
    val data: String? = null
)

@Serializable
data class DeployResult(
    val success: Boolean,
    @SerialName("server_ip")
    val serverIp: String,
    val message: String,
    @SerialName("frontend_url")
    val frontendUrl: String,
    @SerialName("api_url")
    val apiUrl: String
)

@Serializable
data class VdsinaErrorResponse(
    val status: String = "error",
    @SerialName("status_msg")
    val statusMsg: String = "",
    val error: Boolean? = null,
    val code: Int? = null,
    val message: String? = null,
    val details: String? = null
)

// Password-related models

@Serializable
data class ServerPasswordResponse(
    val status: String,
    @SerialName("status_code")
    val statusCode: Int? = null,
    @SerialName("status_msg")
    val statusMsg: String = "",
    val description: String? = null,
    val data: ServerPasswordData? = null
) {
    fun isSuccess(): Boolean = status == "ok"
}

@Serializable
data class ServerPasswordData(
    val password: String? = null
)

@Serializable
data class ChangePasswordRequest(
    val password: String
)

@Serializable
data class ChangePasswordResponse(
    val status: String,
    @SerialName("status_code")
    val statusCode: Int? = null,
    @SerialName("status_msg")
    val statusMsg: String = "",
    val description: String? = null,
    val data: ChangePasswordData? = null
) {
    fun isSuccess(): Boolean = status == "ok"

    fun getValidationErrors(): String? {
        return data?.password?.joinToString("; ")
    }
}

@Serializable
data class ChangePasswordData(
    // password can be either a string (on success) or array of validation errors
    val password: List<String>? = null,
    val message: String? = null
)

// Wait for server result

@Serializable
data class WaitForServerResult(
    val ready: Boolean,
    val message: String,
    val serverStatus: String,
    val serverIp: String? = null,
    val shouldAbort: Boolean = false
)
