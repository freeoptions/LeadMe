package com.leadme.app.model

import kotlinx.serialization.Serializable
import java.util.Locale
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()
fun exitCode(value: String): String = value.trim().removeSuffix("口").trim().uppercase(Locale.ROOT)

@Serializable
data class Point(val x: Double, val y: Double) {
    operator fun plus(other: Point) = Point(x + other.x, y + other.y)
    operator fun minus(other: Point) = Point(x - other.x, y - other.y)
    operator fun times(factor: Double) = Point(x * factor, y * factor)
    companion object { val Origin = Point(0.0, 0.0) }
}

@Serializable
data class Road(val id: String = newId(), val startX: Double, val startY: Double,
                val endX: Double, val endY: Double) {
    val start get() = Point(startX, startY)
    val end get() = Point(endX, endY)
    fun endpoints(start: Point = this.start, end: Point = this.end) =
        copy(startX = start.x, startY = start.y, endX = end.x, endY = end.y)
}

@Serializable
data class MetroExit(val id: String = newId(), val code: String, val x: Double, val y: Double,
                     val note: String? = null) {
    val point get() = Point(x, y)
}

@Serializable
data class MetroStation(val id: String = newId(), val name: String, val exits: List<MetroExit>)

@Serializable
data class BusPlatform(val id: String = newId(), val name: String, val directionLabel: String,
                       val x: Double, val y: Double, val note: String? = null) {
    val point get() = Point(x, y)
}

@Serializable
data class HomeMap(
    val id: String = newId(), val name: String, val archivedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt,
    val roads: List<Road> = emptyList(), val stations: List<MetroStation> = emptyList(),
    val busPlatforms: List<BusPlatform> = emptyList(),
) {
    val points get() = listOf(Point.Origin) + roads.flatMap { listOf(it.start, it.end) } +
        stations.flatMap { it.exits }.map { it.point } + busPlatforms.map { it.point }
    fun contains(id: String) = roads.any { it.id == id } || busPlatforms.any { it.id == id } ||
        stations.any { station -> station.exits.any { it.id == id } }

    fun removeObject(id: String): HomeMap = copy(
        roads = roads.filterNot { it.id == id },
        busPlatforms = busPlatforms.filterNot { it.id == id },
        stations = stations.filterNot { it.id == id }.map {
            it.copy(exits = it.exits.filterNot { exit -> exit.id == id })
        }.filter { it.exits.isNotEmpty() },
    )

    fun movePoint(id: String, point: Point) = copy(
        stations = stations.map { station -> station.copy(exits = station.exits.map {
            if (it.id == id) it.copy(x = point.x, y = point.y) else it
        }) },
        busPlatforms = busPlatforms.map { if (it.id == id) it.copy(x = point.x, y = point.y) else it },
    )
}

/** 快照只记录完整数据操作；视口、点选和吸附开关不进入历史。 */
data class EditSession(
    val baseline: HomeMap, val draft: HomeMap = baseline,
    val undoStack: List<HomeMap> = emptyList(), val snapEnabled: Boolean = false,
) {
    val dirty get() = draft != baseline
    fun apply(next: HomeMap): EditSession = if (next == draft) this
        else copy(draft = next, undoStack = undoStack + draft)
    fun commitDrag(before: HomeMap): EditSession = if (draft == before) this
        else copy(undoStack = undoStack + before)
    fun undo(): EditSession = undoStack.lastOrNull()?.let {
        copy(draft = it, undoStack = undoStack.dropLast(1))
    } ?: this
}

object MapValidation {
    fun validate(home: HomeMap) {
        require(home.name.isNotBlank()) { "住所名称不能为空" }
        require(home.createdAt >= 0 && home.updatedAt >= 0 && (home.archivedAt ?: 0) >= 0) { "时间字段无效" }
        val ids = listOf(home.id) + home.roads.map { it.id } + home.stations.map { it.id } +
            home.stations.flatMap { it.exits }.map { it.id } + home.busPlatforms.map { it.id }
        validateIds(ids)
        require(home.points.all { it.x.isFinite() && it.y.isFinite() }) { "坐标必须是有限数字" }
        require(home.roads.all { it.start != it.end }) { "道路的两个端点不能重合" }
        require(home.stations.map { it.name.trim() }.distinct().size == home.stations.size) { "地铁站名称重复，请选择已有站" }
        home.stations.forEach { station ->
            require(station.name.isNotBlank() && station.exits.isNotEmpty()) { "地铁站需有名称及至少一个出口" }
            val codes = station.exits.map { exitCode(it.code) }
            require(codes.all { it.isNotBlank() }) { "出口编号不能为空" }
            require(codes.distinct().size == codes.size) { "同一地铁站的出口编号不能重复" }
            require(station.exits.all { it.code == exitCode(it.code) }) { "出口编号需统一大小写并移除末尾的口字" }
        }
        require(home.busPlatforms.all { it.name.isNotBlank() && it.directionLabel.isNotBlank() }) { "公交站名和方向不能为空" }
    }

    fun validateIds(ids: List<String>) {
        require(ids.distinct().size == ids.size) { "备份中存在重复 ID" }
        require(ids.all { runCatching { UUID.fromString(it).toString().equals(it, true) }.getOrDefault(false) }) { "对象 ID 无效" }
    }
}
