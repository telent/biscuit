package net.telent.biscuit

import androidx.room.TypeConverter
import java.time.Duration
import java.time.Instant

class RoomTypeAdapters {
    companion object {
        @TypeConverter
        @JvmStatic
        fun fromInstant(value: Instant?): Long? {
            return value?.toEpochMilli()
        }
        @TypeConverter
        @JvmStatic
        fun fromDuration(value: Duration?): Long? {
            return value?.toMillis()
        }

        @TypeConverter
        @JvmStatic
        fun toInstant(value: Long?) :Instant? {
            if(value == null)
                return value
            else
                return Instant.ofEpochMilli(value )
        }
        @TypeConverter
        @JvmStatic
        fun toDuration(value: Long?) :Duration? {
            if(value == null)
                return value
            else
                return Duration.ofMillis(value )
        }
    }
}