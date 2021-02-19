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
import org.osmdroid.util.GeoPoint
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
//        homeViewModel =
//                ViewModelProviders.of(this).get(HomeViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_home, container, false)
//        val textView: TextView = root.findViewById(R.id.text_home)
//        homeViewModel.text.observe(viewLifecycleOwner, Observer {
//            textView.text = it
//        })
        var lastMovingTime = EPOCH
        model.trackpoint.observe(viewLifecycleOwner, {
            val (timestamp, _, _, speed, cadence, wheelRevolutions, movingTime) = it
            val now = LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault())
            if (speed > 0) lastMovingTime = timestamp
            val showMovingTime = (lastMovingTime > timestamp.minusSeconds(30))
            val timestring = if (showMovingTime) {
                val s = movingTime / 1000
                String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, (s % 60))
            } else {
                String.format("%2d:%02d:%02d", now.hour, now.minute, now.second)
            }
            val tv_distance: TextView = root.findViewById(R.id.DistanceText)
            val tv_speed: TextView = root.findViewById(R.id.SpeedText)
            val tv_cadence: TextView = root.findViewById(R.id.CadenceText)
            val tv_time: TextView = root.findViewById(R.id.TimeText)

            if (speed >= 0.0f)
                tv_speed.text = String.format("%.01f", speed) + " km/h"
            if (cadence >= 0.0f)
                tv_cadence.text = String.format("%3.1f rpm", cadence)
            tv_time.text = timestring
            val distanceM = wheelRevolutions * 2.070
            if (distanceM < 5000)
                tv_distance.text = String.format("%.01f m", distanceM)
            else
                tv_distance.text = String.format("%.01f km", distanceM / 1000)
        })
        return root
    }
}