package net.telent.biscuit.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import net.telent.biscuit.R
import net.telent.biscuit.ui.track.TrackViewModel
import java.time.Instant.EPOCH
import java.time.LocalDateTime
import java.time.ZoneId

class HomeFragment : Fragment() {
    private val model: TrackViewModel by activityViewModels()

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_home, container, false)
        var lastMovingTime = EPOCH
        model.trackpoint.observe(viewLifecycleOwner, { (timestamp, _, _, speed, cadence, wheelRevolutions, movingTime) ->
            val now = LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault())
            if (speed > 0) lastMovingTime = timestamp
            val showMovingTime = (lastMovingTime > timestamp.minusSeconds(30))
            val timestring = if (showMovingTime) {
                val s = movingTime.seconds
                String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, (s % 60))
            } else {
                String.format("%2d:%02d:%02d", now.hour, now.minute, now.second)
            }
            fun withView(viewId : Int, f: (v : TextView) -> Unit) { f(root.findViewById(viewId)) }
            if (speed >= 0.0f)
                withView(R.id.SpeedText) { it.text = String.format("%.01f", speed) + " km/h" }
            if (cadence >= 0.0f)
                withView(R.id.CadenceText) { it.text = String.format("%3.1f rpm", cadence) }
            withView(R.id.TimeText) {v ->v.text = timestring }
            val distanceM = wheelRevolutions * 2.070
            withView(R.id.DistanceText) {
                if (distanceM < 5000)
                    it.text = String.format("%.01f m", distanceM)
                else
                    it.text = String.format("%.01f km", distanceM / 1000)
            }
        })
        return root
    }
}