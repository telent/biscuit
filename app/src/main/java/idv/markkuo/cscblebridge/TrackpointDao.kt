package idv.markkuo.cscblebridge

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import java.time.Instant

@Dao
interface TrackpointDao {
    @Query("SELECT * from trackpoint")
    fun getAll(): List<Trackpoint>

    @Query("SELECT * from trackpoint where ts > :startTime and ts < :endTime")
    fun getBetween(startTime: Instant, endTime: Instant): List<Trackpoint>

    @Insert
    fun addPoint(point: Trackpoint)
}