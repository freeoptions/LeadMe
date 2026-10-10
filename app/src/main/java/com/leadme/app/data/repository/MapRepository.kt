package com.leadme.app.data.repository

import androidx.room.withTransaction
import com.leadme.app.data.backup.BackupDocument
import com.leadme.app.data.backup.BackupCodec
import com.leadme.app.data.local.*
import com.leadme.app.model.*

data class SavedMaps(val homes: List<HomeMap>, val currentHomeId: String?)

class MapRepository(private val database: LeadMeDatabase) {
    private val dao = database.maps()

    suspend fun read(): SavedMaps = database.withTransaction {
        SavedMaps(dao.homes().map { readHome(it) }, dao.currentHomeId())
    }

    private suspend fun readHome(home: HomeEntity) = HomeMap(
        home.id, home.name, home.archivedAt, home.createdAt, home.updatedAt,
        dao.roads(home.id).map { Road(it.id, it.startX, it.startY, it.endX, it.endY) },
        dao.stations(home.id).map { station -> MetroStation(station.id, station.name,
            dao.exits(station.id).map { MetroExit(it.id, it.code, it.x, it.y, it.note) }) },
        dao.buses(home.id).map { BusPlatform(it.id, it.name, it.directionLabel, it.x, it.y, it.note) },
    )

    suspend fun create(name: String): String = database.withTransaction {
        require(name.isNotBlank()) { "请填写住所名称" }
        val home = HomeMap(name = name.trim())
        dao.putHome(home.entity())
        dao.putState(AppStateEntity(currentHomeId = home.id))
        home.id
    }

    suspend fun select(id: String) = database.withTransaction {
        val home = requireNotNull(dao.home(id)) { "住所不存在" }
        require(home.archivedAt == null) { "归档住所不能设为默认住所" }
        dao.putState(AppStateEntity(currentHomeId = id))
    }

    suspend fun rename(id: String, name: String) = database.withTransaction {
        require(name.isNotBlank()) { "请填写住所名称" }
        val home = requireNotNull(dao.home(id)) { "住所不存在" }
        require(home.archivedAt == null) { "请先恢复归档住所" }
        dao.putHome(home.copy(name = name.trim(), updatedAt = System.currentTimeMillis()))
    }

    suspend fun archive(id: String) = database.withTransaction {
        val home = requireNotNull(dao.home(id)) { "住所不存在" }
        val now = System.currentTimeMillis()
        dao.putHome(home.copy(archivedAt = now, updatedAt = now))
        if (dao.currentHomeId() == id) {
            dao.putState(AppStateEntity(currentHomeId = dao.homes().firstOrNull { it.archivedAt == null }?.id))
        }
    }

    suspend fun restore(id: String) = database.withTransaction {
        val home = requireNotNull(dao.home(id)) { "住所不存在" }
        dao.putHome(home.copy(archivedAt = null, updatedAt = System.currentTimeMillis()))
        dao.putState(AppStateEntity(currentHomeId = id))
    }

    suspend fun save(draft: HomeMap) = database.withTransaction {
        MapValidation.validate(draft)
        val saved = requireNotNull(dao.home(draft.id)) { "住所不存在" }
        require(saved.archivedAt == null) { "归档住所不可编辑，请先恢复" }
        // 保留住所管理字段，只替换当前住所图内的对象；任一步失败会回滚整个事务。
        dao.clearRoads(draft.id)
        dao.clearStations(draft.id)
        dao.clearBuses(draft.id)
        writeObjects(draft)
        dao.putHome(saved.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun export(): BackupDocument = read().let {
        BackupDocument(currentHomeId = it.currentHomeId, homes = it.homes)
    }

    suspend fun importBackup(document: BackupDocument): Int = database.withTransaction {
        BackupCodec.validate(document)
        val saved = dao.homes()
        val names = saved.map { it.name }.toMutableSet()
        val mapping = mutableMapOf<String, String>()
        val copies = document.homes.map { old ->
            var name = old.name.trim()
            if (name in names) {
                name = "${old.name.trim()}（导入）"
                var number = 2
                while (name in names) { name = "${old.name.trim()}（导入 ${number++}）" }
            }
            names.add(name)
            old.copy(id = newId(), name = name,
                roads = old.roads.map { it.copy(id = newId()) },
                stations = old.stations.map { station -> station.copy(id = newId(),
                    exits = station.exits.map { it.copy(id = newId()) }) },
                busPlatforms = old.busPlatforms.map { it.copy(id = newId()) },
            ).also { mapping[old.id] = it.id }
        }
        copies.forEach { home -> dao.putHome(home.entity()); writeObjects(home) }
        val current = dao.currentHomeId()
        if (saved.none { it.id == current && it.archivedAt == null }) {
            val importedCurrent = document.currentHomeId?.let { mapping[it] }
            val next = copies.find { it.id == importedCurrent && it.archivedAt == null }
                ?: copies.firstOrNull { it.archivedAt == null }
            dao.putState(AppStateEntity(currentHomeId = next?.id))
        }
        copies.size
    }

    private suspend fun writeObjects(home: HomeMap) {
        dao.insertRoads(home.roads.map { RoadEntity(it.id, home.id, it.startX, it.startY, it.endX, it.endY) })
        dao.insertStations(home.stations.map { StationEntity(it.id, home.id, it.name) })
        dao.insertExits(home.stations.flatMap { station -> station.exits.map {
            ExitEntity(it.id, station.id, it.code, it.x, it.y, it.note)
        } })
        dao.insertBuses(home.busPlatforms.map { BusEntity(it.id, home.id, it.name, it.directionLabel, it.x, it.y, it.note) })
    }

    private fun HomeMap.entity() = HomeEntity(id, name, archivedAt, createdAt, updatedAt)
}
