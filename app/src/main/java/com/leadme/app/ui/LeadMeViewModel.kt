package com.leadme.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.leadme.app.LeadMeApplication
import com.leadme.app.data.backup.*
import com.leadme.app.geometry.*
import com.leadme.app.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

enum class Panel { HOMES, CREATE_HOME, RENAME_HOME, MORE, BACKUP, ADD, METRO, EXIT, BUS, DETAIL, IMPORT_PREVIEW }

data class LeadMeState(
    val loading: Boolean = true, val busy: Boolean = false, val loadError: String? = null,
    val homes: List<HomeMap> = emptyList(), val currentHomeId: String? = null,
    val viewingHomeId: String? = null, val example: Boolean = false,
    val editor: EditSession? = null, val selected: String? = null,
    val temporaryRoad: Road? = null, val viewport: Viewport = Viewport(),
    val labelsVisible: Boolean = true, val snapRoadId: String? = null,
    val panel: Panel? = null, val formId: String? = null,
    val confirmDiscard: Boolean = false, val message: String? = null,
    val importPreview: BackupDocument? = null,
) {
    val home get() = if (example) Example.home else homes.find { it.id == viewingHomeId }
    val map get() = editor?.draft ?: home
    val editing get() = editor != null
}

class LeadMeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as LeadMeApplication).repository
    private val mutable = MutableStateFlow(LeadMeState())
    val state = mutable.asStateFlow()
    private var dragBaseline: HomeMap? = null
    private var dragTemporary: Road? = null
    private var snapMemory = SnapMemory()

    init { load() }

    fun load() {
        if (mutable.value.busy) return
        viewModelScope.launch {
            mutable.update { it.copy(loading = true, loadError = null) }
            try {
                val saved = repository.read()
                val current = saved.homes.find { it.id == saved.currentHomeId && it.archivedAt == null }
                    ?: saved.homes.firstOrNull { it.archivedAt == null }
                mutable.update { it.copy(loading = false, homes = saved.homes,
                    currentHomeId = current?.id, viewingHomeId = current?.id) }
                recenter()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.update { it.copy(loading = false, loadError = "无法读取本地数据：${e.message}") }
            }
        }
    }

    fun open(panel: Panel, id: String? = null) {
        val s = mutable.value
        if (s.busy || s.loading || s.temporaryRoad != null) return
        if (s.editing && panel !in setOf(Panel.ADD, Panel.METRO, Panel.EXIT, Panel.BUS)) return
        mutable.update { it.copy(panel = panel, formId = id, message = null) }
    }

    fun closePanel() {
        if (!mutable.value.busy) mutable.update { it.copy(panel = null, formId = null, importPreview = null) }
    }

    fun clearMessage() = mutable.update { it.copy(message = null) }
    fun showExample() { mutable.update { it.copy(example = true, panel = null, selected = null) }; recenter() }

    fun back() {
        val s = mutable.value
        if (s.busy) return
        when {
            s.panel != null -> closePanel()
            s.temporaryRoad != null -> cancelRoad()
            s.editing -> requestCancel()
            s.example -> { mutable.update { it.copy(example = false, selected = null) }; recenter() }
            s.home?.archivedAt != null -> {
                mutable.update { it.copy(viewingHomeId = it.currentHomeId, selected = null) }; recenter()
            }
        }
    }

    fun createHome(name: String) = write {
        val id = repository.create(name)
        reload(id)
        mutable.update { it.copy(example = false, panel = null) }
        recenter()
    }

    fun selectHome(id: String) {
        if (mutable.value.editing) return
        val home = mutable.value.homes.find { it.id == id } ?: return
        if (home.archivedAt != null) {
            mutable.update { it.copy(viewingHomeId = id, example = false, panel = null, selected = null) }
            recenter()
        } else write {
            repository.select(id)
            reload(id)
            mutable.update { it.copy(example = false, panel = null, selected = null) }
            recenter()
        }
    }

    fun renameHome(id: String, name: String) = write {
        repository.rename(id, name); reload(mutable.value.viewingHomeId)
        mutable.update { it.copy(panel = null) }
    }

    fun archiveHome(id: String) = write {
        repository.archive(id); reload(null, useCurrent = true)
        mutable.update { it.copy(panel = null, selected = null, example = false) }; recenter()
    }

    fun restoreHome(id: String) = write {
        repository.restore(id); reload(id)
        mutable.update { it.copy(panel = null, selected = null, example = false) }; recenter()
    }

    fun startEditing() {
        val s = mutable.value
        val home = s.home ?: return
        if (s.busy || s.example || home.archivedAt != null) return
        mutable.update { it.copy(editor = EditSession(home), selected = null, panel = null) }
    }

    fun requestCancel() {
        cancelDrag()
        if (mutable.value.editor?.dirty == true) mutable.update { it.copy(confirmDiscard = true) }
        else discard()
    }

    fun keepEditing() = mutable.update { it.copy(confirmDiscard = false) }
    fun discard() {
        cancelDrag()
        mutable.update { it.copy(editor = null, selected = null, temporaryRoad = null,
            confirmDiscard = false, snapRoadId = null, panel = null) }
    }

    fun save() {
        val s = mutable.value
        if (s.panel != null || s.temporaryRoad != null || dragBaseline != null) return
        val draft = s.editor?.draft ?: return
        write {
            repository.save(draft)
            // 提交成功后退出编辑；如果之后刷新失败，也不能把已提交的事务显示成未保存。
            mutable.update { it.copy(homes = it.homes.map { home -> if (home.id == draft.id) draft else home },
                editor = null, selected = null, message = "整张图已保存") }
            reload(draft.id)
        }
    }

    fun undo() {
        if (mutable.value.busy || mutable.value.temporaryRoad != null) return
        cancelDrag()
        mutable.update { s -> s.copy(editor = s.editor?.undo(), selected = null, snapRoadId = null) }
    }

    fun toggleSnap(enabled: Boolean) = mutable.update {
        it.copy(editor = it.editor?.copy(snapEnabled = enabled), snapRoadId = null)
    }

    fun select(hit: Hit?) {
        val s = mutable.value
        if (s.busy || s.temporaryRoad != null) return
        mutable.update { it.copy(selected = hit?.id,
            panel = if (!s.editing && hit?.part == HitPart.POINT) Panel.DETAIL else null) }
    }

    fun deleteSelected() {
        val s = mutable.value
        val id = s.selected ?: return
        val draft = s.editor?.draft ?: return
        applyMap(draft.removeObject(id))
        mutable.update { it.copy(selected = null) }
    }

    fun deleteStation(id: String) {
        val draft = mutable.value.editor?.draft ?: return
        applyMap(draft.removeObject(id))
        mutable.update { it.copy(panel = null, selected = null) }
    }

    fun resize(width: Double, height: Double) {
        val s = mutable.value
        if (s.viewport.width == width && s.viewport.height == height) return
        cancelDrag()
        mutable.update { it.copy(viewport = it.viewport.copy(width = width, height = height)) }
        if (s.viewport.width <= 1 || s.viewport.height <= 1) recenter()
    }

    fun recenter() {
        val s = mutable.value
        val next = s.viewport.fit((s.map?.points ?: listOf(Point.Origin)) +
            listOfNotNull(s.temporaryRoad?.start, s.temporaryRoad?.end))
        setViewport(next)
    }

    fun transform(previousCenter: Point, currentCenter: Point, zoom: Double) =
        setViewport(mutable.value.viewport.transform(previousCenter, currentCenter, zoom))

    private fun setViewport(viewport: Viewport) = mutable.update {
        it.copy(viewport = viewport, labelsVisible = when {
            viewport.scale >= 0.85 -> true
            viewport.scale < 0.75 -> false
            else -> it.labelsVisible
        })
    }

    fun addRoad() {
        val s = mutable.value
        if (!s.editing) return
        val start = s.viewport.world(Point(s.viewport.width / 2 - 70, s.viewport.height / 2 - 60))
        val end = s.viewport.world(Point(s.viewport.width / 2 + 70, s.viewport.height / 2 - 60))
        val road = Road(startX = start.x, startY = start.y, endX = end.x, endY = end.y)
        mutable.update { it.copy(temporaryRoad = road, selected = road.id, panel = null) }
    }

    fun placeRoad() {
        val s = mutable.value
        val road = s.temporaryRoad ?: return
        val draft = s.editor?.draft ?: return
        val error = applyMap(draft.copy(roads = draft.roads + road))
        if (error == null) mutable.update { it.copy(temporaryRoad = null) }
        else mutable.update { it.copy(message = error) }
    }

    fun cancelRoad() { cancelDrag(); mutable.update { it.copy(temporaryRoad = null, selected = null) } }

    fun beginDrag(): Boolean {
        val s = mutable.value
        if (!s.editing || s.busy) return false
        dragBaseline = s.editor!!.draft
        dragTemporary = s.temporaryRoad
        snapMemory = SnapMemory()
        return true
    }

    fun drag(hit: Hit, delta: Point) {
        val base = dragBaseline ?: return
        val s = mutable.value
        val session = s.editor ?: return
        if (hit.part == HitPart.POINT) {
            val origin = base.stations.flatMap { it.exits }.find { it.id == hit.id }?.point
                ?: base.busPlatforms.find { it.id == hit.id }?.point ?: return
            val target = origin + delta
            val result = if (session.snapEnabled) snapToRoad(target, base.roads, s.viewport.scale, snapMemory)
                else SnapResult(target, SnapMemory())
            snapMemory = result.memory
            mutable.update { it.copy(editor = session.copy(draft = base.movePoint(hit.id, result.point)),
                selected = hit.id, snapRoadId = result.memory.roadId) }
        } else {
            val road = dragTemporary?.takeIf { it.id == hit.id } ?: base.roads.find { it.id == hit.id } ?: return
            val moved = when (hit.part) {
                HitPart.START -> road.endpoints(start = road.start + delta)
                HitPart.END -> road.endpoints(end = road.end + delta)
                else -> road.endpoints(start = road.start + delta, end = road.end + delta)
            }
            if (moved.start == moved.end) return
            if (road.id == dragTemporary?.id) mutable.update { it.copy(temporaryRoad = moved) }
            else mutable.update { it.copy(editor = session.copy(draft = base.copy(
                roads = base.roads.map { old -> if (old.id == hit.id) moved else old }))) }
        }
    }

    fun endDrag() {
        val before = dragBaseline ?: return
        mutable.update { it.copy(editor = it.editor?.commitDrag(before), snapRoadId = null) }
        dragBaseline = null; dragTemporary = null
    }

    fun cancelDrag() {
        val before = dragBaseline ?: return
        mutable.update { it.copy(editor = it.editor?.copy(draft = before), temporaryRoad = dragTemporary, snapRoadId = null) }
        dragBaseline = null; dragTemporary = null
    }

    fun applyBus(id: String?, name: String, direction: String, note: String): String? {
        val map = mutable.value.editor?.draft ?: return "请先进入编辑模式"
        if (name.isBlank() || direction.isBlank()) return "请填写站台名称和方向"
        val old = map.busPlatforms.find { it.id == id }
        val point = old?.point ?: freePositions(1).first()
        val bus = BusPlatform(old?.id ?: newId(), name.trim(), direction.trim(), point.x, point.y, note.trim().ifEmpty { null })
        return finishForm(map.copy(busPlatforms = if (old == null) map.busPlatforms + bus
            else map.busPlatforms.map { if (it.id == id) bus else it }))
    }

    fun applyStation(id: String?, name: String, codesText: String, note: String): String? {
        val map = mutable.value.editor?.draft ?: return "请先进入编辑模式"
        val existing = map.stations.find { it.id == id }
        if (name.isBlank()) return "请填写地铁站名称"
        if (map.stations.any { it.id != id && it.name == name.trim() }) return "该地铁站已存在，请选择已有站添加出口"
        val codes = codesText.split(Regex("[\\s,，、;；]+" )).filter { it.isNotBlank() }.map(::exitCode)
        if (existing == null && codes.isEmpty()) return "至少填写一个出口编号"
        if (codes.any { it.isBlank() }) return "出口编号不能为空"
        val points = freePositions(codes.size)
        val exits = codes.mapIndexed { index, code -> MetroExit(code = code, x = points[index].x,
            y = points[index].y, note = note.trim().ifEmpty { null }) }
        val station = MetroStation(existing?.id ?: newId(), name.trim(), (existing?.exits ?: emptyList()) + exits)
        return finishForm(map.copy(stations = if (existing == null) map.stations + station
            else map.stations.map { if (it.id == id) station else it }))
    }

    fun applyExit(id: String?, stationId: String, code: String, note: String): String? {
        val map = mutable.value.editor?.draft ?: return "请先进入编辑模式"
        if (map.stations.none { it.id == stationId }) return "请选择所属地铁站"
        val normalized = exitCode(code)
        if (normalized.isBlank()) return "请填写出口编号"
        val existing = map.stations.flatMap { it.exits }.find { it.id == id }
        val point = existing?.point ?: freePositions(1).first()
        val exit = MetroExit(existing?.id ?: newId(), normalized, point.x, point.y, note.trim().ifEmpty { null })
        val stations = map.stations.map { station ->
            val exits = if (station.id == stationId) {
                if (station.exits.any { it.id == id }) station.exits.map { if (it.id == id) exit else it }
                else station.exits + exit
            } else station.exits.filterNot { it.id == id }
            station.copy(exits = exits)
        }.filter { it.exits.isNotEmpty() }
        return finishForm(map.copy(stations = stations))
    }

    private fun finishForm(map: HomeMap): String? = applyMap(map).also { error ->
        if (error == null) mutable.update { it.copy(panel = null, formId = null) }
    }

    private fun applyMap(map: HomeMap): String? {
        val s = mutable.value
        if (s.busy || !s.editing) return "当前不可编辑"
        return try {
            MapValidation.validate(map)
            mutable.update { it.copy(editor = it.editor?.apply(map)) }
            null
        } catch (e: IllegalArgumentException) { e.message ?: "请检查输入" }
    }

    private fun freePositions(count: Int): List<Point> {
        val s = mutable.value
        val viewport = s.viewport
        val occupied = (s.map?.stations?.flatMap { it.exits }?.map { it.point } ?: emptyList()) +
            (s.map?.busPlatforms?.map { it.point } ?: emptyList()) + Point.Origin
        val used = occupied.map(viewport::screen).toMutableList()
        val result = mutableListOf<Point>()
        val columns = ((viewport.width - 64) / 64).toInt().coerceAtLeast(1)
        val rows = ((viewport.height - 100) / 64).toInt().coerceAtLeast(1)
        val candidates = (0 until rows).flatMap { row -> (0 until columns).map { col ->
            Point(viewport.width / 2 + (col - (columns - 1) / 2.0) * 64,
                viewport.height / 2 + (row - (rows - 1) / 2.0) * 64)
        } }.sortedBy { abs(it.x - viewport.width / 2) + abs(it.y - viewport.height / 2) }
        repeat(count) { index ->
            val position = candidates.firstOrNull { candidate -> used.none { distance(it, candidate) < 56 } }
                ?: candidates[index % candidates.size] + Point((index % 3) * 8.0, (index % 3) * 8.0)
            used.add(position); result.add(viewport.world(position))
        }
        return result
    }

    fun exportBackup(uri: Uri?) {
        if (uri == null || mutable.value.editing) return
        write {
            val text = BackupCodec.encode(repository.export())
            withContext(Dispatchers.IO) {
                val resolver = getApplication<Application>().contentResolver
                requireNotNull(resolver.openOutputStream(uri, "wt")) { "无法打开目标文件" }
                    .bufferedWriter(Charsets.UTF_8).use { it.write(text) }
            }
            mutable.update { it.copy(message = "备份已导出", panel = null) }
        }
    }

    fun readBackup(uri: Uri?) {
        if (uri == null || mutable.value.editing) return
        write {
            val document = withContext(Dispatchers.IO) {
                val resolver = getApplication<Application>().contentResolver
                val text = requireNotNull(resolver.openInputStream(uri)) { "无法打开备份" }
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
                BackupCodec.decode(text)
            }
            mutable.update { it.copy(importPreview = document, panel = Panel.IMPORT_PREVIEW) }
        }
    }

    fun confirmImport() {
        val document = mutable.value.importPreview ?: return
        write {
            val count = repository.importBackup(document)
            reload(mutable.value.viewingHomeId, useCurrent = mutable.value.home == null)
            mutable.update { it.copy(importPreview = null, panel = null, message = "已新增 $count 个独立住所") }
            recenter()
        }
    }

    private suspend fun reload(viewing: String?, useCurrent: Boolean = false) {
        val saved = repository.read()
        mutable.update { it.copy(homes = saved.homes, currentHomeId = saved.currentHomeId,
            viewingHomeId = if (useCurrent) saved.currentHomeId else viewing) }
    }

    private fun write(block: suspend () -> Unit) {
        val s = mutable.value
        if (s.busy || s.loading || s.loadError != null) return
        mutable.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try { block() }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.update { it.copy(message = "操作失败：${e.message ?: "请重试"}") }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
}
