package com.leadme.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leadme.app.model.HomeMap
import com.leadme.app.ui.theme.MapStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPanels(state: LeadMeState, vm: LeadMeViewModel, onExport: () -> Unit, onImport: () -> Unit) {
    val panel = state.panel ?: return
    val busy by rememberUpdatedState(state.busy)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !busy })
    ModalBottomSheet(onDismissRequest = vm::closePanel, sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp)
            .imePadding().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                    Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            key(panel, state.formId) {
                when (panel) {
                    Panel.HOMES -> HomesPanel(state, vm)
                    Panel.CREATE_HOME -> HomeForm(state, vm, null)
                    Panel.RENAME_HOME -> HomeForm(state, vm, state.homes.find { it.id == state.formId })
                    Panel.MORE -> {
                        Title("你的 LeadMe", "简单记录，完全离线。")
                        ActionRow(Icons.Outlined.Home, "住所管理", "切换、重命名、归档和恢复", !state.busy) { vm.open(Panel.HOMES) }
                        ActionRow(Icons.Outlined.SaveAlt, "备份与恢复", "手动导出与导入本地 JSON 文件", !state.busy) { vm.open(Panel.BACKUP) }
                        ActionRow(Icons.Outlined.Explore, "查看示例", "只读，不会加入个人住所", !state.busy, vm::showExample)
                        Text("LeadMe 1.0 · 只记录相对位置，不保存地址。", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Panel.BACKUP -> {
                        Title("备份与恢复", "保存一份，安心留存。")
                        Text("导出包含所有已保存及已归档住所。备份为明文 JSON，请保存在你信任的位置。")
                        ActionRow(Icons.Outlined.FileUpload, "导出备份", "通过系统文件选择器保存", !state.busy, onExport)
                        ActionRow(Icons.Outlined.FileDownload, "导入备份", "先预览，再新增为独立住所", !state.busy, onImport)
                        Text("导入不会覆盖现有图；重复导入会再次创建副本。示例和未保存草稿不参与备份。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Panel.IMPORT_PREVIEW -> {
                        val document = state.importPreview
                        Title("确认导入", "将新增 ${document?.homes?.size ?: 0} 个独立住所")
                        Text("不覆盖现有内容；名称冲突会追加“（导入）”后缀。重复导入也会新增副本，住所暂不提供删除功能。")
                        document?.homes?.forEach { home ->
                            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                    Text(home.name, style = MaterialTheme.typography.titleMedium)
                                    Text(if (home.archivedAt != null) "已归档 · 导入后只读" else "未归档",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Button(onClick = vm::confirmImport, enabled = !state.busy && document != null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("确认新增这些住所") }
                    }
                    Panel.ADD -> {
                        Title("往图中添加", "添加后拖动摆放，最后统一保存。")
                        ActionRow(Icons.Outlined.HorizontalRule, "直线道路", "用两个端点确定位置和方向", true, vm::addRoad)
                        ActionRow(Icons.Outlined.Subway, "地铁站与出口", "支持一次添加 A、C、E 等多个出口", true) { vm.open(Panel.METRO) }
                        if (state.map?.stations?.isNotEmpty() == true) ActionRow(Icons.Outlined.AddLocationAlt,
                            "已有站的新出口", "归属已有地铁站，独立摆放", true) { vm.open(Panel.EXIT) }
                        ActionRow(Icons.Outlined.DirectionsBus, "公交站台", "道路两侧分别添加，方向分别填写", true) { vm.open(Panel.BUS) }
                    }
                    Panel.METRO -> StationForm(state, vm)
                    Panel.EXIT -> ExitForm(state, vm)
                    Panel.BUS -> BusForm(state, vm)
                    Panel.DETAIL -> DetailPanel(state)
                }
            }
        }
    }
}

@Composable
private fun Title(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, enabled: Boolean, action: () -> Unit) {
    Surface(onClick = action, enabled = enabled, shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp))
        }
    }
}

@Composable
private fun HomesPanel(state: LeadMeState, vm: LeadMeViewModel) {
    Title("我的住所", "搬家后归档旧图，记忆仍然保留。")
    Button(onClick = { vm.open(Panel.CREATE_HOME) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Add, null); Text("创建新住所")
    }
    val active = state.homes.filter { it.archivedAt == null }
    val archived = state.homes.filter { it.archivedAt != null }
    if (active.isEmpty()) Text("还没有可用住所，可以创建或恢复一个。", style = MaterialTheme.typography.bodyMedium)
    active.forEach { HomeRow(it, state, vm) }
    if (archived.isNotEmpty()) {
        Text("已归档 · ${archived.size}", style = MaterialTheme.typography.titleMedium)
        archived.forEach { HomeRow(it, state, vm) }
    }
}

@Composable
private fun HomeRow(home: HomeMap, state: LeadMeState, vm: LeadMeViewModel) {
    Surface(shape = MapStyle.card, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(home.name, style = MaterialTheme.typography.titleMedium)
            Text(if (home.archivedAt != null) "已归档 · 只读" else if (home.id == state.currentHomeId) "当前默认住所" else "独立示意图",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (home.archivedAt == null) {
                    TextButton(onClick = { vm.open(Panel.RENAME_HOME, home.id) }, enabled = !state.busy) { Text("重命名") }
                    TextButton(onClick = { vm.archiveHome(home.id) }, enabled = !state.busy) { Text("归档") }
                } else TextButton(onClick = { vm.restoreHome(home.id) }, enabled = !state.busy) { Text("恢复") }
                TextButton(onClick = { vm.selectHome(home.id) }, enabled = !state.busy) { Text("${if (home.archivedAt != null) "查看" else "打开"}") }
            }
        }
    }
}

@Composable
private fun HomeForm(state: LeadMeState, vm: LeadMeViewModel, home: HomeMap?) {
    var name by rememberSaveable { mutableStateOf(home?.name.orEmpty()) }
    Title(if (home == null) "给家起个名字" else "重命名住所", "只需你熟悉的名称，不需要真实地址。")
    Field("住所名称", name, { name = it }, !state.busy, placeholder = "例如：现在住处")
    Button(onClick = { if (home == null) vm.createHome(name) else vm.renameHome(home.id, name) },
        enabled = !state.busy && name.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(if (home == null) "创建空白示意图" else "保存名称")
    }
}

@Composable
private fun StationForm(state: LeadMeState, vm: LeadMeViewModel) {
    val station = state.map?.stations?.find { it.id == state.formId }
    var name by rememberSaveable { mutableStateOf(station?.name.orEmpty()) }
    var codes by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    Title(if (station == null) "添加地铁站" else "编辑地铁站", "每个出口都会独立显示在图上。")
    if (station == null && state.map?.stations?.isNotEmpty() == true) {
        Text("已有地铁站", style = MaterialTheme.typography.labelLarge)
        state.map?.stations?.forEach { existing ->
            TextButton(onClick = { vm.open(Panel.METRO, existing.id) }) { Text("${existing.name} · 修改或追加出口") }
        }
    }
    Field("地铁站名称", name, { name = it; error = null }, !state.busy)
    if (station != null) {
        Text("已有出口：${station.exits.joinToString("、") { "${it.code}口" }}", style = MaterialTheme.typography.bodyMedium)
    }
    Field(if (station == null) "出口编号" else "追加出口（可选）", codes, { codes = it; error = null }, !state.busy,
        placeholder = "A C E，或 A1、B2")
    Text("用空格、逗号或顿号分隔；自动统一大小写和末尾“口”字。", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Field("这批出口的备注（可选）", note, { note = it; error = null }, !state.busy, singleLine = false)
    Text("每个出口的备注之后也能单独修改。", style = MaterialTheme.typography.bodySmall)
    FormActions(error, state.busy, if (station == null) "添加到图中" else "应用到草稿") {
        error = vm.applyStation(station?.id, name, codes, note)
    }
    if (station != null) TextButton(onClick = { vm.deleteStation(station.id) }, enabled = !state.busy) {
        Text("删除本站及全部出口", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun ExitForm(state: LeadMeState, vm: LeadMeViewModel) {
    val stations = state.map?.stations.orEmpty()
    val originalStation = stations.find { it.exits.any { exit -> exit.id == state.formId } }
    val exit = originalStation?.exits?.find { it.id == state.formId }
    var stationId by rememberSaveable { mutableStateOf(originalStation?.id ?: stations.firstOrNull()?.id.orEmpty()) }
    var code by rememberSaveable { mutableStateOf(exit?.code.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(exit?.note.orEmpty()) }
    var expanded by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    Title(if (exit == null) "添加地铁出口" else "修改地铁出口", "编号、位置和备注都属于这个独立出口。")
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text("所属站：${stations.find { it.id == stationId }?.name ?: "请选择"}", Modifier.weight(1f))
            Icon(Icons.Outlined.ExpandMore, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            stations.forEach { station -> DropdownMenuItem(text = { Text(station.name) }, onClick = {
                stationId = station.id; expanded = false; error = null
            }) }
        }
    }
    Field("出口编号", code, { code = it; error = null }, !state.busy, placeholder = "例如：A1")
    Field("备注（可选）", note, { note = it; error = null }, !state.busy, singleLine = false)
    FormActions(error, state.busy, if (exit == null) "添加到图中" else "应用到草稿") {
        error = vm.applyExit(exit?.id, stationId, code, note)
    }
    if (originalStation != null) TextButton(onClick = { vm.open(Panel.METRO, originalStation.id) }, enabled = !state.busy) {
        Text("修改所属站名称 / 批量追加出口")
    }
}

@Composable
private fun BusForm(state: LeadMeState, vm: LeadMeViewModel) {
    val bus = state.map?.busPlatforms?.find { it.id == state.formId }
    var name by rememberSaveable { mutableStateOf(bus?.name.orEmpty()) }
    var direction by rememberSaveable { mutableStateOf(bus?.directionLabel.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(bus?.note.orEmpty()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    Title(if (bus == null) "添加公交站台" else "修改公交站台", "去程和回程分开摆放，看清道路的哪一侧。")
    Field("站台名称", name, { name = it; error = null }, !state.busy)
    Field("方向", direction, { direction = it; error = null }, !state.busy, placeholder = "例如：往市中心")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("去程", "回程").forEach { value ->
            SuggestionChip(onClick = { direction = value; error = null }, label = { Text(value) }, enabled = !state.busy)
        }
    }
    Field("备注（可选）", note, { note = it; error = null }, !state.busy, singleLine = false)
    FormActions(error, state.busy, if (bus == null) "添加到图中" else "应用到草稿") {
        error = vm.applyBus(bus?.id, name, direction, note)
    }
}

@Composable
private fun DetailPanel(state: LeadMeState) {
    val station = state.map?.stations?.find { it.exits.any { exit -> exit.id == state.selected } }
    val exit = station?.exits?.find { it.id == state.selected }
    val bus = state.map?.busPlatforms?.find { it.id == state.selected }
    if (station != null && exit != null) {
        Icon(Icons.Outlined.Subway, null, Modifier.size(36.dp), tint = MapStyle.metro)
        Title("${station.name} · ${exit.code}口", "独立地铁出口")
        if (!exit.note.isNullOrBlank()) Text(exit.note)
        HorizontalDivider()
        Text("同站出口", style = MaterialTheme.typography.labelLarge)
        Text(station.exits.joinToString(" · ") { "${it.code}口" }, fontWeight = FontWeight.Medium)
    } else if (bus != null) {
        Icon(Icons.Outlined.DirectionsBus, null, Modifier.size(36.dp), tint = MapStyle.bus)
        Title(bus.name, bus.directionLabel)
        if (!bus.note.isNullOrBlank()) Text(bus.note)
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, enabled: Boolean,
    placeholder: String = "", singleLine: Boolean = true) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) },
        placeholder = { Text(placeholder) }, singleLine = singleLine, minLines = if (singleLine) 1 else 2,
        enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
}

@Composable
private fun FormActions(error: String?, busy: Boolean, text: String, action: () -> Unit) {
    if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    Button(onClick = action, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(text) }
    Text("仅更新本轮草稿，点击编辑页的“保存”才会正式保存。", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
