package com.leadme.app.data.local

import androidx.room.*

@Entity(tableName = "homes")
data class HomeEntity(@PrimaryKey val id: String, val name: String,
    @ColumnInfo(name = "archived_at") val archivedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long)

@Entity(tableName = "road_segments", foreignKeys = [ForeignKey(entity = HomeEntity::class,
    parentColumns = ["id"], childColumns = ["home_id"], onDelete = ForeignKey.CASCADE)], indices = [Index("home_id")])
data class RoadEntity(@PrimaryKey val id: String, @ColumnInfo(name = "home_id") val homeId: String,
    @ColumnInfo(name = "start_x") val startX: Double, @ColumnInfo(name = "start_y") val startY: Double,
    @ColumnInfo(name = "end_x") val endX: Double, @ColumnInfo(name = "end_y") val endY: Double)

@Entity(tableName = "metro_stations", foreignKeys = [ForeignKey(entity = HomeEntity::class,
    parentColumns = ["id"], childColumns = ["home_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("home_id"), Index(value = ["home_id", "name"], unique = true)])
data class StationEntity(@PrimaryKey val id: String, @ColumnInfo(name = "home_id") val homeId: String, val name: String)

@Entity(tableName = "metro_exits", foreignKeys = [ForeignKey(entity = StationEntity::class,
    parentColumns = ["id"], childColumns = ["station_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("station_id"), Index(value = ["station_id", "code"], unique = true)])
data class ExitEntity(@PrimaryKey val id: String, @ColumnInfo(name = "station_id") val stationId: String,
    val code: String, val x: Double, val y: Double, val note: String?)

@Entity(tableName = "bus_platforms", foreignKeys = [ForeignKey(entity = HomeEntity::class,
    parentColumns = ["id"], childColumns = ["home_id"], onDelete = ForeignKey.CASCADE)], indices = [Index("home_id")])
data class BusEntity(@PrimaryKey val id: String, @ColumnInfo(name = "home_id") val homeId: String,
    val name: String, @ColumnInfo(name = "direction_label") val directionLabel: String,
    val x: Double, val y: Double, val note: String?)

@Entity(tableName = "app_state", foreignKeys = [ForeignKey(entity = HomeEntity::class,
    parentColumns = ["id"], childColumns = ["current_home_id"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("current_home_id")])
data class AppStateEntity(@PrimaryKey val id: Int = 1, @ColumnInfo(name = "current_home_id") val currentHomeId: String?)

@Dao
interface MapDao {
    @Query("SELECT * FROM homes ORDER BY created_at, id") suspend fun homes(): List<HomeEntity>
    @Query("SELECT * FROM homes WHERE id = :id") suspend fun home(id: String): HomeEntity?
    @Query("SELECT * FROM road_segments WHERE home_id = :id ORDER BY rowid") suspend fun roads(id: String): List<RoadEntity>
    @Query("SELECT * FROM metro_stations WHERE home_id = :id ORDER BY rowid") suspend fun stations(id: String): List<StationEntity>
    @Query("SELECT * FROM metro_exits WHERE station_id = :id ORDER BY rowid") suspend fun exits(id: String): List<ExitEntity>
    @Query("SELECT * FROM bus_platforms WHERE home_id = :id ORDER BY rowid") suspend fun buses(id: String): List<BusEntity>
    @Query("SELECT current_home_id FROM app_state WHERE id = 1") suspend fun currentHomeId(): String?
    @Upsert suspend fun putHome(home: HomeEntity)
    @Insert suspend fun insertRoads(roads: List<RoadEntity>)
    @Insert suspend fun insertStations(stations: List<StationEntity>)
    @Insert suspend fun insertExits(exits: List<ExitEntity>)
    @Insert suspend fun insertBuses(buses: List<BusEntity>)
    @Upsert suspend fun putState(state: AppStateEntity)
    @Query("DELETE FROM road_segments WHERE home_id = :id") suspend fun clearRoads(id: String)
    @Query("DELETE FROM metro_stations WHERE home_id = :id") suspend fun clearStations(id: String)
    @Query("DELETE FROM bus_platforms WHERE home_id = :id") suspend fun clearBuses(id: String)
}

@Database(entities = [HomeEntity::class, RoadEntity::class, StationEntity::class, ExitEntity::class,
    BusEntity::class, AppStateEntity::class], version = 1, exportSchema = true)
abstract class LeadMeDatabase : RoomDatabase() { abstract fun maps(): MapDao }
