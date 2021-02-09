package net.telent.biscuit

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [ Trackpoint::class ], version = 1, exportSchema = false)
@TypeConverters(RoomTypeAdapters::class)
abstract class BiscuitDatabase : RoomDatabase() {
    abstract fun trackpointDao(): TrackpointDao
}