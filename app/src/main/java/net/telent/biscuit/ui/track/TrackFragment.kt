package net.telent.biscuit.ui.track

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import net.telent.biscuit.R
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class TrackFragment : Fragment() {
    private lateinit var map : MapView
    private val model: TrackViewModel by activityViewModels()

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_track, container, false)
//        val textView: TextView = root.findViewById(R.id.text_dashboard)

        map = root.findViewById(R.id.map)

        // this will be replaced by our track marker.
        // we add it first so we know we can replae it at position 0
        map.overlayManager.add(Polyline())

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.zoomController.setZoomInEnabled(true)
        map.zoomController.setZoomOutEnabled(true)
        map.setMultiTouchControls(true)

        val gpsMyLocationProvider = GpsMyLocationProvider(this.requireActivity().baseContext).let {
            it.locationUpdateMinDistance = 1.0f // [m]  // Set the minimum distance for location updates
            it.locationUpdateMinTime = 1000
            it
        }
        val locOverlay = MyLocationNewOverlay(gpsMyLocationProvider, map)
        map.overlays.add(locOverlay)

        map.controller.setZoom(18.0)
        model.trackpoint.observe(viewLifecycleOwner, {
            if(it.lat != null && it.lng != null)
                map.controller.setCenter(GeoPoint(it.lat.toDouble(), it.lng.toDouble()))
        })
        model.track.observe(viewLifecycleOwner ,{
            val line = Polyline()
            line.setPoints(it)
            map.overlayManager[0] = line
        })
        return root
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