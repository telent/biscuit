package net.telent.biscuit

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val migrations = arrayOf(
    object:  Migration(1,2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("alter table trackpoint add column revs (integer)")
        }
    },
    object:  Migration(2,3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("alter table trackpoint add column movingtime (integer)")
        }
    }
)

@Database(entities = [ Trackpoint::class ], version = 2, exportSchema = false)
@TypeConverters(RoomTypeAdapters::class)
abstract class BiscuitDatabase : RoomDatabase() {
    abstract fun trackpointDao(): TrackpointDao

    companion object {
        @Volatile
        private var INSTANCE: BiscuitDatabase? = null

        fun getInstance(context: Context): BiscuitDatabase {
            synchronized(this) {
                var instance = INSTANCE
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.applicationContext,
                            BiscuitDatabase::class.java,
                            "tracks_database")
                            .addMigrations(*migrations)
                            .build()

                    INSTANCE = instance
                }
                return instance
            }
        }
    }
}

