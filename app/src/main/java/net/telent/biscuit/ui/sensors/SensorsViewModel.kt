package net.telent.biscuit.ui.sensors

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import net.telent.biscuit.SensorSummaries


class SensorsViewModel : ViewModel() {

    private val _states = MutableLiveData<SensorSummaries>()
    val states: LiveData<SensorSummaries> = _states

    fun update(data : SensorSummaries) {
        _states.value=data
    }
}