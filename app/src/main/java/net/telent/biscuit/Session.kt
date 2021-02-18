package net.telent.biscuit

import android.os.Parcelable
import androidx.annotation.Nullable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import java.time.Instant

@Entity @Parcelize
data class Session(
        @PrimaryKey @ColumnInfo(name = "s") @JvmField val start: Instant,
        @Nullable @ColumnInfo(name = "e")  val end: Instant? = null
) : Parcelable