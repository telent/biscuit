package net.telent.biscuit

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import java.time.Instant

@Dao
interface SessionDao {
    @Query("SELECT * from session")
    fun getAll() : LiveData<List<Session>>

    @Query("select * from session where e is null")
    fun getOpen(): Session

    @Query("update session set e = :end where e is null")
    fun close(end : Instant)

    @Insert
    fun create(s: Session)

    fun start(start: Instant) {
        this.close(start)
        val session = Session(start=start, end=null)
        Log.d("HEY", "inserting $session")
        this.create(session)
    }
}
