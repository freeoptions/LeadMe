package com.leadme.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.leadme.app.data.backup.BackupDocument
import com.leadme.app.data.local.*
import com.leadme.app.data.repository.MapRepository
import com.leadme.app.model.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 使用真实 SQLite/Room，既不 mock DAO，也不替换事务行为。 */
@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var database: LeadMeDatabase
    private lateinit var repository: MapRepository

    @Before fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LeadMeDatabase::class.java).build()
        repository = MapRepository(database)
    }

    @After fun closeDatabase() { database.close() }

    @Test fun draftIsIsolatedAndSavePreservesOtherHomes() = runBlocking {
        val firstId = repository.create("家一")
        val secondId = repository.create("家二")
        val original = repository.read().homes.first { it.id == firstId }
        val draft = original.copy(roads = listOf(Road(startX = -1.0, startY = 1.0, endX = 2.0, endY = 1.0)))
        assertTrue(repository.read().homes.first { it.id == firstId }.roads.isEmpty())
        repository.save(draft)
        val saved = repository.read()
        assertEquals(draft.roads, saved.homes.first { it.id == firstId }.roads)
        assertTrue(saved.homes.first { it.id == secondId }.roads.isEmpty())
    }

    @Test fun saveConstraintFailureRollsBackDeletedChildren() = runBlocking {
        val firstId = repository.create("家一")
        val first = repository.read().homes.single().copy(roads = listOf(Road(startX = 0.0, startY = 0.0, endX = 10.0, endY = 0.0)))
        repository.save(first)
        val secondId = repository.create("家二")
        val second = repository.read().homes.first { it.id == secondId }.copy(
            roads = listOf(Road(startX = 0.0, startY = 2.0, endX = 10.0, endY = 2.0)))
        repository.save(second)
        val conflictingDraft = first.copy(roads = listOf(second.roads.single()))
        assertTrue(runCatching { repository.save(conflictingDraft) }.isFailure)
        assertEquals(first.roads, repository.read().homes.first { it.id == firstId }.roads)
        assertEquals(second.roads, repository.read().homes.first { it.id == secondId }.roads)
    }

    @Test fun archiveLastHomeClearsCurrentAndBlocksMapWrites() = runBlocking {
        val id = repository.create("家")
        val original = repository.read().homes.single()
        repository.archive(id)
        assertNull(repository.read().currentHomeId)
        assertTrue(runCatching { repository.save(original) }.isFailure)
        repository.restore(id)
        assertEquals(id, repository.read().currentHomeId)
        assertNull(repository.read().homes.single().archivedAt)
    }

    @Test fun importingRepeatedlyRemapsIdsAndRetainsExistingCurrent() = runBlocking {
        val id = repository.create("家")
        val backupHome = Example.home.copy(name = "家")
        val document = BackupDocument(currentHomeId = backupHome.id, homes = listOf(backupHome))
        repository.importBackup(document)
        repository.importBackup(document)
        val saved = repository.read()
        assertEquals(id, saved.currentHomeId)
        assertEquals(setOf("家", "家（导入）", "家（导入 2）"), saved.homes.map { it.name }.toSet())
        val copies = saved.homes.filter { it.id != id }
        assertNotEquals(copies[0].stations[0].id, copies[1].stations[0].id)
        assertNotEquals(backupHome.id, copies[0].id)
        assertEquals(backupHome.stations[0].exits.map { it.point }, copies[0].stations[0].exits.map { it.point })
        assertEquals(backupHome.busPlatforms.map { it.directionLabel }, copies[0].busPlatforms.map { it.directionLabel })
    }

    @Test fun importFailureRollsBackEntireBatchAndSelection() = runBlocking {
        val id = repository.create("原有住所")
        // 真实数据库约束在第二个住所写入时失败，验证第一个住所也被回滚。
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_import BEFORE INSERT ON homes
            WHEN NEW.name = '拒绝写入'
            BEGIN SELECT RAISE(ABORT, 'transaction test'); END
        """.trimIndent())
        val document = BackupDocument(homes = listOf(HomeMap(name = "先写入"), HomeMap(name = "拒绝写入")))
        assertTrue(runCatching { repository.importBackup(document) }.isFailure)
        assertEquals(listOf("原有住所"), repository.read().homes.map { it.name })
        assertEquals(id, repository.read().currentHomeId)
    }

    @Test fun actualForeignKeysRejectOrphanExits() = runBlocking {
        assertTrue(runCatching {
            database.maps().insertExits(listOf(ExitEntity(newId(), newId(), "A", 1.0, 1.0, null)))
        }.isFailure)
    }

    @Test fun emptyDeviceRestoresBackupsCurrentHomeAndArchiveFlags() = runBlocking {
        val archived = HomeMap(name = "旧家", archivedAt = 100L)
        val active = HomeMap(name = "现在住处")
        repository.importBackup(BackupDocument(currentHomeId = active.id, homes = listOf(archived, active)))
        val saved = repository.read()
        assertEquals(saved.homes.first { it.name == active.name }.id, saved.currentHomeId)
        assertEquals(100L, saved.homes.first { it.name == archived.name }.archivedAt ?: -1L)
        assertNotEquals(active.id, saved.currentHomeId)
    }

    @Test fun diskDatabaseRetainsExplicitlySavedMapAfterReopening() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "leadme-persistence-test-${newId()}.db"
        var disk: LeadMeDatabase? = null
        try {
            val first = Room.databaseBuilder(context, LeadMeDatabase::class.java, name).build()
            disk = first
            val firstRepository = MapRepository(first)
            val id = firstRepository.create("持久化验证")
            val home = firstRepository.read().homes.single()
            val draft = home.copy(stations = Example.home.stations, busPlatforms = Example.home.busPlatforms)
            firstRepository.save(draft)
            first.close()
            val reopened = Room.databaseBuilder(context, LeadMeDatabase::class.java, name).build()
            disk = reopened
            val saved = MapRepository(reopened).read()
            assertEquals(id, saved.currentHomeId)
            assertEquals(draft.stations, saved.homes.single().stations)
            assertEquals(draft.busPlatforms, saved.homes.single().busPlatforms)
        } finally {
            disk?.close()
            context.deleteDatabase(name)
        }
    }
}
