package net.telent.biscuit

import android.util.Log
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import java.time.Instant
import java.util.List

@Dao
interface SessionDao {
    @Query("SELECT * from session")
    fun getAll() : List<Session>

    @Query("select * from session where e is null")
    fun getOpen(): Session

    @Query("update session set e = :end where e is null")
    fun close(end : Instant) : Unit

    @Insert
    fun create(s: Session)

    fun start(start: Instant) {
        this.close(start)
        val sess = Session(start=start, end=null)
        Log.d("HEY", "inserting $sess")
        this.create(sess)
    }
}
