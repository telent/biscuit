package net.telent.biscuit.ui.track

import android.graphics.Canvas
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import net.telent.biscuit.BiscuitDatabase
import net.telent.biscuit.R
import net.telent.biscuit.Trackpoint
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.IconOverlay
import org.osmdroid.views.overlay.Polyline

class TrackFragment : Fragment() {
    private lateinit var map : MapView
    private lateinit var youAreHere: IconOverlay
    private val model: TrackViewModel by activityViewModels()
    private var previousLat : Double = 0.0
    private var previousLng : Double = 0.0

    override fun onCreate(savedInstanceState : Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_track, container, false)

        map = root.findViewById(R.id.map)

        var bearing : Float? = null

        // this will be replaced by our track marker.
        // we add it first so we know we can replace it at position 0
        map.overlayManager.add(Polyline())

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.zoomController.setZoomInEnabled(true)
        map.zoomController.setZoomOutEnabled(true)
        map.setMultiTouchControls(true)

        val icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_bike_abov)
        youAreHere = object : IconOverlay(GeoPoint(0.0,0.0), icon) {
            override fun draw(c : Canvas, p : Projection) {
                val b = bearing ?: null
                if(b!= null)  this.mBearing = b
                super.draw(c, p)
            }
        }
        map.overlays.add(youAreHere)

        map.controller.setZoom(20.0)
        model.trackpoint.observe(viewLifecycleOwner, {
            bearing = bearingTo(it)
            if (bearing != null) {
                Log.d("moved", " ${map.mapCenter} $it $bearing")
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

    override fun onCreateOptionsMenu(menu : Menu, inflater : MenuInflater) {
        inflater.inflate(R.menu.track_action_menu, menu)
    }

    private val db by lazy {
        BiscuitDatabase.getInstance(requireActivity())
    }

    override fun onOptionsItemSelected(item : MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_tracks -> {
                val trackPickerView =  layoutInflater.inflate(R.layout.track_picker, null)
                val layout = trackPickerView.findViewById<LinearLayout>(R.id.track_picker)
                val tracks = db.sessionDao().getAll()
                tracks.observe(viewLifecycleOwner) {
                    layout.removeAllViews()
                    it.forEach { s ->
                        val v = TextView(requireContext())
                        v.layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1.0f  )
                        v.text = s.start.toString()
                        v.setTextSize(TypedValue.COMPLEX_UNIT_PT, 9.0F)
                        Log.d("tag", "$v ${s.start}")
                        layout.addView(v)
                    }
                }
                Log.d("track", "chose ${item.itemId}")
                TrackPicker(trackPickerView).show(childFragmentManager, TrackPicker.TAG )
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun bearingTo(tp: Trackpoint): Float? {
        if (tp.lat != null && tp.lng != null) {
            val from = Location("none").apply {
                latitude = previousLat
                longitude = previousLng
            }
            val to = Location("none").apply {
                latitude = tp.lat; longitude = tp.lng
            }
            if (from.distanceTo(to) > 1) {
                previousLat = tp.lat
                previousLng = tp.lng
                return from.bearingTo(to)
            }
        }
        return null
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