package net.telent.biscuit

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val migrations = arrayOf(
        object:  Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("alter table trackpoint add column revs integer")
            }
        },
        object:  Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("alter table trackpoint add column movingtime integer")
            }
        },
        object:  Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 3-4 is a create table
            }
        },
        object:  Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("alter table trackpoint add column distance real not null default 0")
                database.execSQL("update trackpoint set distance = revs * 2.105")
            }
        }
)

@Database(entities = [ Trackpoint::class, Session::class ],
        version = 5, exportSchema = false)
@TypeConverters(RoomTypeAdapters::class)
abstract class BiscuitDatabase : RoomDatabase() {
    abstract fun trackpointDao(): TrackpointDao
    abstract fun sessionDao(): SessionDao

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
