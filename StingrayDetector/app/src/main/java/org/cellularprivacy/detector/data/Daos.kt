package org.cellularprivacy.detector.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ObservedCellDao {
    @Insert suspend fun insert(cell: ObservedCellEntity): Long

    @Query("SELECT * FROM observed_cell ORDER BY timestampMs DESC LIMIT :limit")
    fun recent(limit: Int = 200): Flow<List<ObservedCellEntity>>

    @Query("SELECT COUNT(DISTINCT cellKey) FROM observed_cell")
    suspend fun distinctCellCount(): Int

    @Query("DELETE FROM observed_cell WHERE timestampMs < :cutoffMs")
    suspend fun purgeOlderThan(cutoffMs: Long)
}

@Dao
interface DetectionEventDao {
    @Insert suspend fun insert(event: DetectionEventEntity): Long

    @Query("SELECT * FROM detection_event ORDER BY timestampMs DESC LIMIT :limit")
    fun recent(limit: Int = 100): Flow<List<DetectionEventEntity>>

    @Query("DELETE FROM detection_event WHERE timestampMs < :cutoffMs")
    suspend fun purgeOlderThan(cutoffMs: Long)
}


@Dao
interface AnfrSiteDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertAll(sites: List<AnfrSiteEntity>)

    @Query("SELECT * FROM anfr_site")
    fun all(): Flow<List<AnfrSiteEntity>>

    @Query("SELECT COUNT(*) FROM anfr_site")
    fun count(): Flow<Int>

    @Query("SELECT DISTINCT city FROM anfr_site")
    fun cities(): Flow<List<String>>

    @Query("DELETE FROM anfr_site")
    suspend fun clear()
}
