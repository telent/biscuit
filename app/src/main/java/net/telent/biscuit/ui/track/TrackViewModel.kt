package net.telent.biscuit.ui.track

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import net.telent.biscuit.Trackpoint
import org.osmdroid.util.GeoPoint

class TrackViewModel : ViewModel() {

    private val _tpt = MutableLiveData<Trackpoint>().apply {
        value = Trackpoint(java.time.Instant.now(), 51.9, 0.2)
    }
    val trackpoint: LiveData<Trackpoint> = _tpt

    private val _track = MutableLiveData<ArrayList<GeoPoint>>()
    val track: LiveData<ArrayList<GeoPoint>> = _track

    fun move(tp: Trackpoint) {
        _tpt.value = tp
        if(tp.lat != null && tp.lng !=null) {
            val p = _track.value ?: ArrayList()
            p.add(GeoPoint(tp.lat, tp.lng))
            _track.value = p
        }
    }
    fun get() {
        _tpt.value
    }
}