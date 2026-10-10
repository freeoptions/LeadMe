package com.leadme.app.data.backup

import com.leadme.app.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.time.Instant

@Serializable
data class BackupDocument(
    val format: String = "home-transit-map", val schemaVersion: Int = 1,
    val exportedAt: String = Instant.now().toString(), val currentHomeId: String? = null,
    val homes: List<HomeMap>,
)

object BackupCodec {
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }

    fun encode(document: BackupDocument): String { validate(document); return json.encodeToString(document) }

    fun decode(text: String): BackupDocument {
        val root = json.parseToJsonElement(text).jsonObject
        required(root, "format", "schemaVersion", "exportedAt", "currentHomeId", "homes")
        require(root["format"]?.jsonPrimitive?.content == "home-transit-map") { "不是 LeadMe 交通示意图备份" }
        require(root["schemaVersion"]?.jsonPrimitive?.intOrNull == 1) { "暂不支持此备份版本" }
        // 防止模型的创建默认值把损坏文件中的缺失字段静默补成新数据。
        root.getValue("homes").jsonArray.forEach { element ->
            val home = element.jsonObject
            required(home, "id", "name", "archivedAt", "createdAt", "updatedAt", "roads", "stations", "busPlatforms")
            home.getValue("roads").jsonArray.forEach {
                required(it.jsonObject, "id", "startX", "startY", "endX", "endY")
            }
            home.getValue("stations").jsonArray.forEach { station ->
                required(station.jsonObject, "id", "name", "exits")
                station.jsonObject.getValue("exits").jsonArray.forEach {
                    required(it.jsonObject, "id", "code", "x", "y")
                }
            }
            home.getValue("busPlatforms").jsonArray.forEach {
                required(it.jsonObject, "id", "name", "directionLabel", "x", "y")
            }
        }
        val decoded = json.decodeFromJsonElement<BackupDocument>(root)
        val normalized = decoded.copy(homes = decoded.homes.map { home -> home.copy(
            name = home.name.trim(),
            stations = home.stations.map { station -> station.copy(name = station.name.trim(),
                exits = station.exits.map { it.copy(code = exitCode(it.code)) }) },
            busPlatforms = home.busPlatforms.map { it.copy(name = it.name.trim(), directionLabel = it.directionLabel.trim()) },
        ) })
        validate(normalized)
        return normalized
    }

    fun validate(document: BackupDocument) {
        require(document.format == "home-transit-map") { "备份格式不正确" }
        require(document.schemaVersion == 1) { "暂不支持此备份版本" }
        require(runCatching { Instant.parse(document.exportedAt) }.isSuccess) { "备份时间无效" }
        document.homes.forEach(MapValidation::validate)
        MapValidation.validateIds(document.homes.flatMap { home ->
            listOf(home.id) + home.roads.map { it.id } + home.stations.map { it.id } +
                home.stations.flatMap { it.exits }.map { it.id } + home.busPlatforms.map { it.id }
        })
        require(document.currentHomeId == null || document.homes.any {
            it.id == document.currentHomeId && it.archivedAt == null
        }) { "备份的当前住所不存在或已归档" }
    }

    private fun required(value: JsonObject, vararg keys: String) {
        require(keys.all { it in value }) { "备份缺少必要字段：${keys.filterNot { it in value }.joinToString()}" }
    }
}
