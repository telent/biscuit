package net.telent.biscuit.ui.track

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import net.telent.biscuit.Trackpoint

class TrackViewModel : ViewModel() {

    private val _tpt = MutableLiveData<Trackpoint>().apply {
        value = Trackpoint(java.time.Instant.now(), 51.9, 0.2)
    }
    val trackpoint: LiveData<Trackpoint> = _tpt

    fun move(tp: Trackpoint) {
        _tpt.value = tp
    }
    fun get() {
        _tpt.value
    }
}