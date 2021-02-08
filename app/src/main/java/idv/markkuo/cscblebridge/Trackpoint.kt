package idv.markkuo.cscblebridge

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity
data class Trackpoint(
        @PrimaryKey @ColumnInfo(name = "ts") val timestamp: Instant,
        @ColumnInfo(name = "lat") val lat: Double?,
        @ColumnInfo(name = "lng") val lng: Double?,
        @ColumnInfo(name = "speed") val speed: Float?,
        @ColumnInfo(name = "cad") val cadence: Float?
)
