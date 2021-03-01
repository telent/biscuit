package net.telent.biscuit.ui.sensors

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import net.telent.biscuit.R
import net.telent.biscuit.SensorSummary

class SensorsFragment : Fragment() {
    private val model: SensorsViewModel by activityViewModels()

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_sensors, container, false)
        model.states.observe(viewLifecycleOwner, {
            fun formatSummary(s: SensorSummary) : String{
                return "${s.name} ${s.sensorName}: ${s.state}"
            }
            if(it != null) {
                val speed: TextView = root.findViewById(R.id.speed_sensor_state)
                val cadence: TextView = root.findViewById(R.id.cadence_sensor_state)
                val heart: TextView = root.findViewById(R.id.heart_sensor_state)
                val stride: TextView = root.findViewById(R.id.stride_sensor_state)
                speed.text = formatSummary(it.speed)
                cadence.text = formatSummary(it.cadence)
                heart.text = formatSummary(it.heart)
                stride.text = formatSummary(it.stride)
            }
        })

        return root
    }
}