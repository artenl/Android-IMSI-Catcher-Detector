package org.cellularprivacy.detector.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ObservedCellEntity::class, DetectionEventEntity::class, AnfrSiteEntity::class],
    version = 2,
    exportSchema = true
)
abstract class DetectorDatabase : RoomDatabase() {
    abstract fun observedCellDao(): ObservedCellDao
    abstract fun detectionEventDao(): DetectionEventDao
    abstract fun anfrSiteDao(): AnfrSiteDao

    companion object {
        @Volatile private var instance: DetectorDatabase? = null

        fun get(context: Context): DetectorDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DetectorDatabase::class.java,
                    "detector.db"
                )
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
