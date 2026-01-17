package ru.sber.cb.aichallenge_one.mcp_vdsina.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * VDSina API response models
 */

@Serializable
data class Datacenter(
    val id: Int,
    val name: String,
    val country: String,
    val active: Boolean
)

@Serializable
data class DatacentersResponse(
    val datacenters: List<Datacenter>
)

@Serializable
data class ServerPlan(
    val id: Int,
    val name: String,
    val cost: Double,
    val period: String,
    val cpu: Int,
    @SerialName("ram_gb")
    val ramGb: Int,
    @SerialName("disk_gb")
    val diskGb: Int,
    val active: Boolean,
    val enable: Boolean? = null
)

@Serializable
data class ServerPlansResponse(
    val plans: List<ServerPlan>
)

@Serializable
data class ServerGroup(
    val id: Int,
    val name: String,
    val active: Boolean,
    val description: String? = null
)

@Serializable
data class ServerGroupsResponse(
    val groups: List<ServerGroup>
)

@Serializable
data class Template(
    val id: Int,
    val name: String,
    val active: Boolean,
    @SerialName("ssh_key_supported")
    val sshKeySupported: Boolean
)

@Serializable
data class TemplatesResponse(
    val templates: List<Template>
)

@Serializable
data class SshKey(
    val id: Int,
    val name: String
)

@Serializable
data class SshKeysResponse(
    val keys: List<SshKey>
)

@Serializable
data class CreateSshKeyRequest(
    val name: String,
    val data: String
)

@Serializable
data class CreateSshKeyResponse(
    val id: Int,
    val message: String
)

@Serializable
data class ServerInfo(
    val id: Int,
    val name: String,
    val status: String,
    @SerialName("status_text")
    val statusText: String? = null,
    val ip: String? = null,
    val created: String,
    val datacenter: DatacenterInfo? = null,
    val plan: PlanInfo? = null,
    val template: TemplateInfo? = null
)

@Serializable
data class DatacenterInfo(
    val id: Int,
    val name: String
)

@Serializable
data class PlanInfo(
    val id: Int,
    val name: String
)

@Serializable
data class TemplateInfo(
    val id: Int,
    val name: String
)

@Serializable
data class ServersResponse(
    val servers: List<ServerInfo>
)

@Serializable
data class CreateServerRequest(
    val name: String,
    @SerialName("datacenter_id")
    val datacenterId: Int,
    @SerialName("plan_id")
    val planId: Int,
    @SerialName("template_id")
    val templateId: Int,
    @SerialName("ssh_key_id")
    val sshKeyId: Int? = null
)

@Serializable
data class CreateServerResponse(
    @SerialName("server_id")
    val serverId: Int,
    val message: String,
    @SerialName("estimated_time")
    val estimatedTime: String
)

@Serializable
data class DeleteServerResponse(
    val message: String
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
    val error: Boolean,
    val code: Int,
    val message: String,
    val details: String? = null
)
