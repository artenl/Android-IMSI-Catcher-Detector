package org.cellularprivacy.detector.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ObservedCellEntity::class, DetectionEventEntity::class],
    version = 1,
    exportSchema = true
)
abstract class DetectorDatabase : RoomDatabase() {
    abstract fun observedCellDao(): ObservedCellDao
    abstract fun detectionEventDao(): DetectionEventDao

    companion object {
        @Volatile private var instance: DetectorDatabase? = null

        fun get(context: Context): DetectorDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DetectorDatabase::class.java,
                    "detector.db"
                ).build().also { instance = it }
            }
    }
}
