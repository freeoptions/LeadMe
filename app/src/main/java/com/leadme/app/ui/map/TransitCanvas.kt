package com.leadme.app.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.leadme.app.geometry.*
import com.leadme.app.model.*
import com.leadme.app.ui.LeadMeState
import com.leadme.app.ui.LeadMeViewModel
import com.leadme.app.ui.theme.MapStyle
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TransitCanvas(state: LeadMeState, vm: LeadMeViewModel, modifier: Modifier = Modifier) {
    val map = state.map ?: return
    val displayDensity = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val roadEdge = colors.outlineVariant
    val roadFill = colors.surfaceContainer
    val selectedColor = colors.primary
    val viewport = state.viewport
    Box(modifier.background(colors.background).clipToBounds()) {
        Box(Modifier.fillMaxSize()
            .onSizeChanged { with(displayDensity) { vm.resize(it.width.toDp().value.toDouble(), it.height.toDp().value.toDouble()) } }
            .pointerInput(map.id, state.editing, state.busy, displayDensity.density) {
                if (state.busy) return@pointerInput
                val pixelsPerDp = displayDensity.density
                fun Offset.dpPoint() = Point((x / pixelsPerDp).toDouble(), (y / pixelsPerDp).toDouble())
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val initial = vm.state.value
                    val source = initial.map ?: return@awaitEachGesture
                    val hit = hitTest(source, initial.viewport, down.position.dpPoint(), initial.selected, initial.temporaryRoad)
                    val draggable = initial.editing && hit != null &&
                        (initial.temporaryRoad == null || initial.temporaryRoad.id == hit.id) &&
                        (hit.part == HitPart.POINT || hit.id == initial.selected)
                    var moved = false
                    var multi = false
                    var dragging = false
                    var completed = false
                    try {
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.isConsumed }) break
                            val pressed = event.changes.count { it.pressed }
                            if (pressed >= 2) {
                                if (!multi) { vm.cancelDrag(); dragging = false; multi = true }
                                val previous = event.calculateCentroid(useCurrent = false)
                                val current = event.calculateCentroid(useCurrent = true)
                                val zoom = event.calculateZoom()
                                if (previous.x.isFinite() && current.x.isFinite() && zoom.isFinite()) {
                                    vm.transform(previous.dpPoint(), current.dpPoint(), zoom.toDouble())
                                }
                                moved = true
                                event.changes.forEach { it.consume() }
                            } else if (multi) {
                                event.changes.firstOrNull { it.pressed && it.previousPressed }?.let {
                                    vm.transform(it.previousPosition.dpPoint(), it.position.dpPoint(), 1.0)
                                }
                                event.changes.forEach { it.consume() }
                            } else {
                                val change = event.changes.find { it.id == down.id }
                                if (change != null) {
                                    val total = change.position - down.position
                                    val crossed = !moved && total.getDistance() > viewConfiguration.touchSlop
                                    if (crossed) {
                                        moved = true
                                        if (draggable) dragging = vm.beginDrag()
                                    }
                                    if (moved) {
                                        if (dragging && hit != null) {
                                            val scale = initial.viewport.scale
                                            vm.drag(hit, Point(total.x / pixelsPerDp / scale, -total.y / pixelsPerDp / scale))
                                        } else {
                                            val previous = if (crossed) down.position else change.previousPosition
                                            vm.transform(previous.dpPoint(), change.position.dpPoint(), 1.0)
                                        }
                                        change.consume()
                                    }
                                }
                            }
                            if (pressed == 0) {
                                if (dragging) vm.endDrag()
                                else if (!moved) vm.select(hit)
                                completed = true
                            }
                        } while (!completed)
                    } finally {
                        if (!completed) vm.cancelDrag()
                    }
                }
            }) {
            Canvas(Modifier.fillMaxSize()) {
                fun Point.offset() = Offset((x * displayDensity.density).toFloat(), (y * displayDensity.density).toFloat())
                val step = 24.dp.toPx()
                // 背景点阵只辅助观察平移，不表达真实距离。
                val origin = viewport.screen(Point.Origin).offset()
                var x = origin.x % step
                while (x < size.width) {
                    var y = origin.y % step
                    while (y < size.height) {
                        drawCircle(roadEdge.copy(alpha = 0.5f), 0.8.dp.toPx(), Offset(x, y)); y += step
                    }
                    x += step
                }
                (map.roads + listOfNotNull(state.temporaryRoad)).forEach { road ->
                    val start = viewport.screen(road.start).offset()
                    val end = viewport.screen(road.end).offset()
                    val highlighted = road.id == state.selected || road.id == state.snapRoadId
                    drawLine(if (highlighted) selectedColor.copy(alpha = 0.65f) else roadEdge,
                        start, end, MapStyle.roadWidth.toPx(), StrokeCap.Round)
                    drawLine(roadFill, start, end, MapStyle.roadInset.toPx(), StrokeCap.Round)
                    if (state.editing && road.id == state.selected) {
                        listOf(start, end).forEach {
                            drawCircle(selectedColor, 10.dp.toPx(), it)
                            drawCircle(roadFill, 5.dp.toPx(), it)
                        }
                    }
                }
            }
            MapMarker(viewport.screen(Point.Origin), Icons.Outlined.Home, MapStyle.home,
                "家", "", true, false, "家，固定在示意图原点", null)
            map.stations.forEach { station -> station.exits.forEach { exit ->
                MapMarker(viewport.screen(exit.point), Icons.Outlined.Subway, MapStyle.metro,
                    "${station.name} · ${exit.code}口", "", state.labelsVisible || state.selected == exit.id,
                    state.selected == exit.id, "${station.name}，${exit.code}口，${exit.note.orEmpty()}",
                    { vm.select(Hit(exit.id, HitPart.POINT)) })
            } }
            map.busPlatforms.forEach { bus ->
                MapMarker(viewport.screen(bus.point), Icons.Outlined.DirectionsBus, MapStyle.bus,
                    bus.name, bus.directionLabel, state.labelsVisible || state.selected == bus.id,
                    state.selected == bus.id, "${bus.name}，${bus.directionLabel}，${bus.note.orEmpty()}",
                    { vm.select(Hit(bus.id, HitPart.POINT)) })
            }
        }
        Surface(Modifier.align(Alignment.TopEnd).padding(MapStyle.canvasPadding),
            shape = CircleShape, color = colors.surfaceContainer) {
            Text("北 ↑", Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.labelLarge)
        }
        if (map.roads.isEmpty() && map.stations.isEmpty() && map.busPlatforms.isEmpty() && state.temporaryRoad == null) {
            Text(if (state.editing) "从一条道路开始\n再把出口和站台放到熟悉的位置" else "这里是你的家\n点击「编辑」画出周围的交通",
                Modifier.align(Alignment.TopCenter).padding(top = 90.dp, start = 24.dp, end = 24.dp),
                color = colors.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        }
        FilledTonalIconButton(onClick = vm::recenter,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).size(52.dp)) {
            Icon(Icons.Outlined.MyLocation, contentDescription = "回到家，居中展示全图")
        }
        Row(Modifier.align(Alignment.BottomStart).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Legend(Icons.Outlined.Subway, MapStyle.metro, "地铁口")
            Legend(Icons.Outlined.DirectionsBus, MapStyle.bus, "公交站")
        }
    }
}

@Composable
private fun MapMarker(position: Point, icon: ImageVector, color: Color, title: String, subtitle: String,
    showLabel: Boolean, selected: Boolean, description: String, onSelect: (() -> Unit)?) {
    val density = LocalDensity.current
    // 避免超远的画布节点发生整数溢出；不改变存储坐标，也不聚合出口。
    if (!position.x.isFinite() || !position.y.isFinite() || abs(position.x) > 100000 || abs(position.y) > 100000) return
    Box(Modifier.offset { with(density) {
        IntOffset((position.x * this.density).roundToInt() - 24.dp.roundToPx(),
            (position.y * this.density).roundToInt() - 24.dp.roundToPx())
    } }.size(MapStyle.touchSize).semantics(mergeDescendants = true) {
        contentDescription = description
        if (onSelect != null) { role = Role.Button; onClick(label = "查看完整信息") { onSelect(); true } }
    }.focusable(onSelect != null), contentAlignment = Alignment.Center) {
        if (selected) Surface(Modifier.size(46.dp), color = color.copy(alpha = 0.2f), shape = CircleShape) {}
        Surface(Modifier.size(MapStyle.markerSize), color = color, shape = CircleShape, shadowElevation = 2.dp) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(21.dp), tint = Color.White) }
        }
        if (showLabel) {
            Column(Modifier.align(Alignment.TopCenter).offset(y = MapStyle.labelOffset)
                .requiredWidth(MapStyle.labelWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(color = MaterialTheme.colorScheme.background.copy(alpha = 0.94f), shape = MaterialTheme.shapes.small) {
                    Column(Modifier.padding(horizontal = 5.dp, vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center)
                        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend(icon: ImageVector, color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, Modifier.size(16.dp), tint = color)
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
