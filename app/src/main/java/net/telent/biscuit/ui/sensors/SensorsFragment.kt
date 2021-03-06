package net.telent.biscuit.ui.sensors

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
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
            val layout : LinearLayout = root.findViewById(R.id.sensor_states)
            fun formatSummary(s: SensorSummary) : String{
                return "${s.name} ${s.sensorName}: ${s.state}"
            }
            layout.removeAllViews()
            listOf(it.speed, it.cadence, it.heart, it.stride, it.position).forEach { s ->
                val v = TextView(requireContext())
                v.layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1.0f  )
                v.text = formatSummary(s)
                v.setTextSize(TypedValue.COMPLEX_UNIT_PT, 12.0F)
                layout.addView(v)
            }
        })
        return root
    }
}