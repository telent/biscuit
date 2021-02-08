package idv.markkuo.cscblebridge

import androidx.room.TypeConverter
import java.time.Instant

class RoomTypeAdapters {
    companion object {
        @TypeConverter
        @JvmStatic
        fun fromInstant(value: Instant): Long {
            return value.toEpochMilli()
        }

        @TypeConverter
        @JvmStatic
        fun toInstant(value: Long):Instant {
            return Instant.ofEpochMilli(value)
        }
    }
}