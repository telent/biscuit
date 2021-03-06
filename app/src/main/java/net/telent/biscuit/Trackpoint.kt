package net.telent.biscuit

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import java.time.Duration
import java.time.Instant

@Entity @Parcelize
data class Trackpoint(
        @PrimaryKey @ColumnInfo(name = "ts") @JvmField val timestamp: Instant,
        @ColumnInfo(name = "lat") val lat: Double?,
        @ColumnInfo(name = "lng") val lng: Double?,
        @ColumnInfo(name = "speed") val speed: Float = -1.0f,
        @ColumnInfo(name = "cad") val cadence: Float = -1.0f,
       @ColumnInfo(name = "revs") val wheelRevolutions: Long = 0, // no longer used
        @ColumnInfo(name = "movingtime") val movingTime: Duration = Duration.ZERO,
        @ColumnInfo(name = "distance") val distance : Float = 0.0f
) : Parcelable
