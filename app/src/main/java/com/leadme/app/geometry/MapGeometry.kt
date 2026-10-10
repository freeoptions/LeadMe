package com.leadme.app.geometry

import com.leadme.app.model.*
import kotlin.math.*

/** 屏幕坐标以 dp 表示；Y 轴与世界坐标相反。 */
data class Viewport(val width: Double = 1.0, val height: Double = 1.0,
                    val scale: Double = 1.0, val pan: Point = Point.Origin) {
    fun screen(p: Point) = Point(width / 2 + pan.x + p.x * scale, height / 2 + pan.y - p.y * scale)
    fun world(p: Point) = Point((p.x - width / 2 - pan.x) / scale, -(p.y - height / 2 - pan.y) / scale)
    fun transform(previousCenter: Point, currentCenter: Point, zoom: Double): Viewport {
        val anchor = world(previousCenter)
        val nextScale = (scale * zoom).coerceIn(minOf(scale, 0.05), 4.0)
        return copy(scale = nextScale, pan = Point(
            currentCenter.x - width / 2 - anchor.x * nextScale,
            currentCenter.y - height / 2 + anchor.y * nextScale,
        ))
    }
    fun fit(points: List<Point>): Viewport {
        val maxX = points.maxOfOrNull { abs(it.x) } ?: 0.0
        val maxY = points.maxOfOrNull { abs(it.y) } ?: 0.0
        val sx = if (maxX == 0.0) 1.0 else ((width / 2 - 80).coerceAtLeast(1.0) / maxX)
        val sy = if (maxY == 0.0) 1.0 else ((height / 2 - 85).coerceAtLeast(1.0) / maxY)
        return copy(scale = minOf(1.0, sx, sy).coerceAtLeast(java.lang.Double.MIN_NORMAL), pan = Point.Origin)
    }
}

fun distance(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)
fun projection(point: Point, road: Road): Point {
    val delta = road.end - road.start
    val length = hypot(delta.x, delta.y)
    if (length == 0.0 || !length.isFinite()) return road.start
    val unit = delta * (1 / length)
    val fromStart = point - road.start
    val along = (fromStart.x * unit.x + fromStart.y * unit.y).coerceIn(0.0, length)
    return road.start + unit * along
}

enum class HitPart { POINT, ROAD, START, END }
data class Hit(val id: String, val part: HitPart)

fun hitTest(map: HomeMap, viewport: Viewport, screen: Point, selected: String?, temporary: Road? = null): Hit? {
    val selectedRoad = temporary ?: map.roads.find { it.id == selected }
    selectedRoad?.let {
        if (distance(viewport.screen(it.start), screen) <= 24) return Hit(it.id, HitPart.START)
        if (distance(viewport.screen(it.end), screen) <= 24) return Hit(it.id, HitPart.END)
    }
    val points = map.stations.flatMap { it.exits }.map { it.id to it.point } + map.busPlatforms.map { it.id to it.point }
    points.minByOrNull { distance(viewport.screen(it.second), screen) }?.let {
        if (distance(viewport.screen(it.second), screen) <= 24) return Hit(it.first, HitPart.POINT)
    }
    val world = viewport.world(screen)
    return (map.roads + listOfNotNull(temporary)).minByOrNull { distance(projection(world, it), world) }?.let {
        if (distance(projection(world, it), world) * viewport.scale <= 24) Hit(it.id, HitPart.ROAD) else null
    }
}

data class SnapMemory(val roadId: String? = null, val side: Double = 0.0)
data class SnapResult(val point: Point, val memory: SnapMemory)

fun snapToRoad(point: Point, roads: List<Road>, scale: Double, memory: SnapMemory): SnapResult {
    val ranked = roads.map { it to distance(point, projection(point, it)) * scale }.sortedBy { it.second }
    val closest = ranked.firstOrNull()?.takeIf { it.second <= 32 } ?: return SnapResult(point, SnapMemory())
    val retained = ranked.find { it.first.id == memory.roadId && it.second <= 32 }
    val road = if (retained != null && retained.second <= closest.second + 8) retained.first else closest.first
    val vector = road.end - road.start
    val length = hypot(vector.x, vector.y)
    if (length == 0.0 || !length.isFinite()) return SnapResult(point, SnapMemory())
    val normal = Point(-vector.y / length, vector.x / length)
    val projection = projection(point, road)
    val delta = point - projection
    val signed = (delta.x * normal.x + delta.y * normal.y) * scale
    val side = if (abs(signed) > 0.5) sign(signed) else if (memory.roadId == road.id) memory.side else 0.0
    if (side == 0.0) return SnapResult(point, SnapMemory(road.id, 0.0))
    return SnapResult(projection + normal * (28 / scale * side), SnapMemory(road.id, side))
}
