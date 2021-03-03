package net.telent.biscuit.ui.track

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import net.telent.biscuit.R
import net.telent.biscuit.Trackpoint
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.IconOverlay
import org.osmdroid.views.overlay.Polyline

class TrackFragment : Fragment() {
    private lateinit var map : MapView
    private lateinit var youAreHere: IconOverlay
    private val model: TrackViewModel by activityViewModels()
    private var previousLat : Double = 0.0
    private var previousLng : Double = 0.0

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_track, container, false)

        map = root.findViewById(R.id.map)

        // this will be replaced by our track marker.
        // we add it first so we know we can replace it at position 0
        map.overlayManager.add(Polyline())

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.zoomController.setZoomInEnabled(true)
        map.zoomController.setZoomOutEnabled(true)
        map.setMultiTouchControls(true)

        val icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_bike_abov)
        youAreHere = IconOverlay(GeoPoint(0.0,0.0), icon)
        map.overlays.add(youAreHere)

        map.controller.setZoom(20.0)
        model.trackpoint.observe(viewLifecycleOwner, {
            if (haveMoved(it)) {
                Log.d("moved", " ${map.mapCenter} ${it}")
                val loc = GeoPoint(it.lat!!.toDouble(), it.lng!!.toDouble())
                youAreHere.moveTo(loc, map)
                map.controller.setCenter(loc)
            }
        })
        model.track.observe(viewLifecycleOwner ,{
            val line = Polyline()
            line.setPoints(it)
            map.overlayManager[0] = line
        })
        return root
    }

    private fun haveMoved( it: Trackpoint): Boolean {
        return if(it.lat != null && it.lng != null) {
            val moved = it.lat != previousLat || it.lng != previousLng
            previousLat = it.lat
            previousLng = it.lng
            moved
        } else
            false
    }

    override fun onResume() {
        super.onResume()
        map.onResume() //needed for compass, my location overlays, v6.0.0 and up
    }

    override fun onPause() {
        super.onPause()
        map.onPause() //needed for compass, my location overlays, v6.0.0 and up
    }
}