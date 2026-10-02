package org.cellularprivacy.detector.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A recorded observation of a cell, with the position where it was seen. */
@Entity(tableName = "observed_cell")
data class ObservedCellEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cellKey: String,
    val rat: String,
    val mcc: Int?,
    val mnc: Int?,
    val areaCode: Int?,
    val cellId: Long?,
    val physicalId: Int?,
    val arfcn: Int?,
    val dbm: Int?,
    val lat: Double?,
    val lon: Double?,
    val timestampMs: Long
)

/** A detection event: which heuristic fired, its score, and the context. */
@Entity(tableName = "detection_event")
data class DetectionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val heuristicId: String,
    val title: String,
    val detail: String,
    val score: Int,
    val threatLevel: String,
    val cellKey: String?,
    val lat: Double?,
    val lon: Double?,
    val timestampMs: Long
)
