package net.telent.biscuit.ui.sensors

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import net.telent.biscuit.R

class SensorsFragment : Fragment() {

    private lateinit var notificationsViewModel: SensorsViewModel

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        notificationsViewModel =
                ViewModelProviders.of(this).get(SensorsViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_sensors, container, false)
        return root
    }
}