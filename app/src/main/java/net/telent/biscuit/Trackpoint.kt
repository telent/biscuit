package net.telent.biscuit

import android.annotation.SuppressLint
import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import java.time.Instant

@Entity @Parcelize
data class Trackpoint(
        @PrimaryKey @ColumnInfo(name = "ts") val timestamp: Instant,
        @ColumnInfo(name = "lat") val lat: Double?,
        @ColumnInfo(name = "lng") val lng: Double?,
        @ColumnInfo(name = "speed") val speed: Float = -1.0f,
        @ColumnInfo(name = "cad") val cadence: Float = -1.0f
) : Parcelable
