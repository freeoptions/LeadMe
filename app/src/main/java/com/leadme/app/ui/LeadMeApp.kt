package com.leadme.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leadme.app.ui.map.TransitCanvas
import com.leadme.app.ui.theme.MapStyle
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun LeadMeApp(vm: LeadMeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"), vm::exportBackup)
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), vm::readBackup)
    LaunchedEffect(state.message, state.panel) {
        if (state.panel == null) state.message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }
    BackHandler(enabled = state.editing || state.example || state.home?.archivedAt != null || state.busy || state.panel != null) { vm.back() }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { if (!state.loading && state.loadError == null) Header(state, vm) },
        bottomBar = {
            if (state.editing) EditorToolbar(state, vm)
            else if (state.home != null) Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Text(if (state.example) "只读示例 · 试试缩放、平移和点击站点"
                    else if (state.home?.archivedAt != null) "已归档 · 恢复住所后可继续编辑"
                    else "记住出口，也记住道路的这一侧。",
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.loadError != null -> Column(Modifier.align(Alignment.Center).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("本地数据暂时无法打开", style = MaterialTheme.typography.titleLarge)
                    Text(state.loadError.orEmpty(), color = MaterialTheme.colorScheme.error)
                    Button(onClick = vm::load) { Text("重新读取") }
                }
                state.map != null -> TransitCanvas(state, vm, Modifier.fillMaxSize())
                else -> Welcome(vm, state.homes.isNotEmpty())
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
    AppPanels(state, vm,
        onExport = { export.launch("LeadMe-交通备份-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.json") },
        onImport = { importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
    if (state.confirmDiscard) AlertDialog(
        onDismissRequest = vm::keepEditing,
        title = { Text("放弃本次未保存的修改？") },
        text = { Text("本轮修改和撤回记录将被清除，已保存的示意图会保留。") },
        confirmButton = { TextButton(onClick = vm::discard) { Text("放弃修改") } },
        dismissButton = { TextButton(onClick = vm::keepEditing) { Text("继续编辑") } },
    )
}

@Composable
private fun Header(state: LeadMeState, vm: LeadMeViewModel) {
    Surface {
        Column(Modifier.statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (state.editing) {
                    TextButton(onClick = { if (state.temporaryRoad != null) vm.cancelRoad() else vm.requestCancel() }, enabled = !state.busy) { Text("取消") }
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text("编辑示意图", style = MaterialTheme.typography.titleMedium)
                        Text(if (state.busy) "正在保存…" else "未保存 · 本轮修改仅留在草稿",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = vm::save, enabled = !state.busy && state.temporaryRoad == null && state.panel == null) { Text("保存") }
                } else {
                    if (state.example || state.home?.archivedAt != null) {
                        IconButton(onClick = vm::back, enabled = !state.busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("LeadMe", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        if (state.home == null) Text("家的周围", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleLarge)
                        else TextButton(onClick = { vm.open(Panel.HOMES) }, enabled = !state.busy) {
                            Text(state.home?.name.orEmpty(), Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(Icons.Outlined.ExpandMore, "切换住所", Modifier.size(20.dp))
                        }
                    }
                    if (state.home != null && !state.example) {
                        if (state.home?.archivedAt != null) TextButton(
                            onClick = { state.home?.id?.let(vm::restoreHome) }, enabled = !state.busy) { Text("恢复") }
                        else FilledTonalButton(onClick = vm::startEditing, enabled = !state.busy) {
                            Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("编辑")
                        }
                    }
                    IconButton(onClick = { vm.open(Panel.MORE) }, enabled = !state.busy) { Icon(Icons.Outlined.MoreHoriz, "更多") }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun EditorToolbar(state: LeadMeState, vm: LeadMeViewModel) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 6.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (state.temporaryRoad != null) {
                Text("拖动两端调整道路，拖动线段整体移动", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = vm::cancelRoad) { Text("取消放置") }
                    Button(onClick = vm::placeRoad) { Text("放置道路") }
                }
            } else {
                val map = state.map
                val bus = map?.busPlatforms?.find { it.id == state.selected }
                val station = map?.stations?.find { it.exits.any { exit -> exit.id == state.selected } }
                val exit = station?.exits?.find { it.id == state.selected }
                val road = map?.roads?.find { it.id == state.selected }
                if (bus != null || exit != null || road != null) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(when {
                                bus != null -> bus.name
                                exit != null -> "${station?.name} · ${exit.code}口"
                                else -> "直线道路"
                            }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(bus?.directionLabel ?: if (road != null) "拖动端点或整体移动" else "拖动图标调整位置",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (road == null) IconButton(onClick = { vm.open(if (bus != null) Panel.BUS else Panel.EXIT, state.selected) }, enabled = !state.busy) {
                            Icon(Icons.Outlined.Edit, "修改地点信息")
                        }
                        IconButton(onClick = vm::deleteSelected, enabled = !state.busy) { Icon(Icons.Outlined.DeleteOutline, "从草稿中删除，可撤回") }
                    }
                } else {
                    // 与选中状态预留相同高度，避免首次拖动触发布局变化而中断手势。
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("画下熟悉的方向", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text("拖动地点；道路先点选再调整", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.open(Panel.ADD) }, enabled = !state.busy) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("添加")
                    }
                    IconButton(onClick = vm::undo, enabled = !state.busy && state.editor?.undoStack?.isNotEmpty() == true) {
                        Icon(Icons.AutoMirrored.Outlined.Undo, "撤回上一次操作")
                    }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Switch(checked = state.editor?.snapEnabled == true, onCheckedChange = vm::toggleSnap, enabled = !state.busy)
                        Text("吸附到道路", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun Welcome(vm: LeadMeViewModel, hasHomes: Boolean) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("每一次出门，\n都有熟悉的方向。", style = MaterialTheme.typography.headlineLarge)
        Text("把家附近的道路、地铁口和公交站，\n整理成一张你看得懂的简图。", style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        WelcomeSketch()
        Text("自己的简图，留在自己手机里。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { vm.open(Panel.CREATE_HOME) }, Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = MapStyle.card) {
            Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("创建我的住所")
        }
        OutlinedButton(onClick = vm::showExample, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("先看看示例") }
        if (hasHomes) TextButton(onClick = { vm.open(Panel.HOMES) }, Modifier.align(Alignment.CenterHorizontally)) { Text("查看与恢复归档住所") }
    }
}

@Composable
private fun WelcomeSketch() {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth().height(210.dp), color = colors.surfaceContainer, shape = MapStyle.card) {
        BoxWithConstraints {
            Canvas(Modifier.fillMaxSize()) {
                drawLine(colors.outlineVariant, Offset(0f, size.height * .35f), Offset(size.width, size.height * .35f), 14.dp.toPx(), StrokeCap.Round)
                drawLine(colors.outlineVariant, Offset(size.width * .28f, 0f), Offset(size.width * .28f, size.height), 14.dp.toPx(), StrokeCap.Round)
            }
            listOf(Triple(.50f, .66f, Icons.Outlined.Home), Triple(.18f, .18f, Icons.Outlined.Subway),
                Triple(.76f, .51f, Icons.Outlined.DirectionsBus)).forEachIndexed { index, (x, y, icon) ->
                Surface(Modifier.offset(x = maxWidth * x - 22.dp, y = maxHeight * y - 22.dp).size(44.dp), shape = CircleShape,
                    color = listOf(MapStyle.home, MapStyle.metro, MapStyle.bus)[index]) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = androidx.compose.ui.graphics.Color.White) }
                }
            }
            Text("家的周围", Modifier.align(Alignment.BottomEnd).padding(18.dp), style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant)
        }
    }
}
