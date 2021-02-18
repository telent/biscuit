package net.telent.biscuit

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.preference.PreferenceManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import net.telent.biscuit.BiscuitService.LocalBinder
import net.telent.biscuit.ui.track.TrackViewModel
import org.osmdroid.config.Configuration
import java.time.Instant.EPOCH
import java.time.LocalDateTime
import java.time.ZoneId


class BikeActivity : AppCompatActivity() {
    private var receiver: MainActivityReceiver? = null
    private var serviceIsBound = false
    private var mService: BiscuitService? = null
    private val mServiceIntent by lazy {
        Intent(applicationContext, BiscuitService::class.java)
    }
    override fun onCreateOptionsMenu(menu : Menu) :Boolean {
        val inflater = getMenuInflater()
        inflater.inflate(R.menu.action_menu, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        return when (item.itemId) {
            R.id.action_refresh -> {
                startService(Intent(this, BiscuitService::class.java).let {
                    it.putExtra("refresh_sensors", 1)
                })
                Log.d(TAG, "request poll sensors again")
                true
            }
            R.id.action_power_off -> {
                val stopServiceIntent = Intent(this, BiscuitService::class.java).let {
                    it.putExtra("stop_service", 1)
                }
                Log.d(TAG, ""+stopServiceIntent)
                startService(stopServiceIntent)
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bike)

        val navView: BottomNavigationView = findViewById(R.id.nav_view)
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_container)!! as NavHostFragment
        val navController = navHostFragment.navController

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(setOf(
                R.id.navigation_home, R.id.navigation_track, R.id.navigation_sensors))
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        ensureLocationPermission(1)
        ensureServiceRunning(mServiceIntent)

        this.applicationContext.let {
            Configuration.getInstance().load(it, PreferenceManager.getDefaultSharedPreferences(it))
        }

        receiver = MainActivityReceiver()
        registerReceiver(receiver, IntentFilter(BiscuitService.INTENT_NAME))
    }

    private fun ensureServiceRunning(mServiceIntent: Intent) {
        if (!serviceIsBound) {
            Log.d(TAG, "Starting Service")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(mServiceIntent)
            else
                startService(mServiceIntent)

            // Bind to the service so we can interact with it
            if (!bindService(mServiceIntent, connection, Context.BIND_AUTO_CREATE)) {
                Log.d(TAG, "Failed to bind to service")
            } else {
                serviceIsBound = true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ensureLocationPermission(1)
        ensureServiceRunning(mServiceIntent)
        // XXX maybe signal the service to attempt to reconnect sensors
        // if they're not running
    }

    /** Defines callbacks for service binding, passed to bindService()  */
    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as LocalBinder
            mService = binder.service
            serviceIsBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            serviceIsBound = false
        }
    }

    // Unbind from the service
    private fun unbindService() {
        if (serviceIsBound) {
            unbindService(connection)
            serviceIsBound = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
        unbindService()
    }

    private fun ensureLocationPermission(locationRequestCode : Int) {
        if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                            Manifest.permission.ACCESS_FINE_LOCATION)) {
                ActivityCompat.requestPermissions(this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), locationRequestCode)
            } else {
                ActivityCompat.requestPermissions(this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), locationRequestCode)
            }
        }
    }

    private inner class MainActivityReceiver : BroadcastReceiver() {
        var lastMovingTime = EPOCH!!

        override fun onReceive(context: Context, intent: Intent) {
            val statusBSD = intent.getStringExtra("bsd_service_status") // bicycle speed
            val statusBC = intent.getStringExtra("bc_service_status") // bicycle cadence
            val trackpoint = intent.getParcelableExtra("trackpoint") ?: Trackpoint(EPOCH, null,null)
            val instant = trackpoint.timestamp
            val speed = trackpoint.speed
            val cadence = trackpoint.cadence
            val now = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
            if (speed > 0) lastMovingTime = instant
            val showMovingTime = (lastMovingTime > instant.minusSeconds(30))
            val timestring = if (showMovingTime) {
                val s = trackpoint.movingTime / 1000
                String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, (s % 60))
            } else {
                String.format("%2d:%02d:%02d", now.hour, now.minute, now.second)
            }
            val dest = findNavController(R.id.nav_host_fragment_container).currentDestination

            when(dest?.label) {
                "Home" -> {
                    val tv_distance: TextView = findViewById(R.id.DistanceText)
                    val tv_speed: TextView = findViewById(R.id.SpeedText)
                    val tv_cadence: TextView = findViewById(R.id.CadenceText)
                    val tv_speed_state: TextView = findViewById(R.id.SpeedState)
                    val tv_cadence_state: TextView = findViewById(R.id.CadenceState)
                    val tv_time: TextView = findViewById(R.id.TimeText)

                    runOnUiThread {
                        if (statusBSD != null)
                            tv_speed_state.text = statusBSD
                        if (speed >= 0.0f)
                            tv_speed.text = String.format("%.01f", speed) + " km/h"
                        if (statusBC != null)
                            tv_cadence_state.text = statusBC
                        if (cadence >= 0.0f)
                            tv_cadence.text = String.format("%3.1f rpm", cadence)
                        tv_time.text = timestring
                        val distanceM = trackpoint.wheelRevolutions * 2.070
                        if (distanceM < 5000)
                            tv_distance.text = String.format("%.01f m", distanceM)
                        else
                            tv_distance.text = String.format("%.01f km", distanceM / 1000)
                    }
                }
                "Track" -> {
                    val vm: TrackViewModel  by viewModels()
                    vm.move(trackpoint)
                }
            }
        }
    }

    companion object {
        private val TAG = BikeActivity::class.java.simpleName
    }
}