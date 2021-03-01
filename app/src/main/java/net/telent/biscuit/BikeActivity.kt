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
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import net.telent.biscuit.BiscuitService.LocalBinder
import net.telent.biscuit.ui.sensors.SensorsViewModel
import net.telent.biscuit.ui.track.TrackViewModel
import org.osmdroid.config.Configuration


class BikeActivity : AppCompatActivity() {
    private var receiver: MainActivityReceiver? = null
    private var serviceIsBound = false
    private var mService: BiscuitService? = null
    private val mServiceIntent by lazy {
        Intent(applicationContext, BiscuitService::class.java)
    }
    override fun onCreateOptionsMenu(menu : Menu) :Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.action_menu, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        return when (item.itemId) {
            R.id.action_refresh -> {
                startService(Intent(this, BiscuitService::class.java).putExtra("refresh_sensors", 1))
                Log.d(TAG, "request poll sensors again")
                true
            }
            R.id.action_power_off -> {
                val stopServiceIntent = Intent(this, BiscuitService::class.java).putExtra("stop_service", 1)
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
        override fun onReceive(context: Context, intent: Intent) {
            val trackpoint: Trackpoint? = intent.getParcelableExtra("trackpoint")
            val sensors: SensorSummaries? = intent.getParcelableExtra("sensor_state")
            if (trackpoint != null) {
                val vm: TrackViewModel by viewModels()
                vm.move(trackpoint)
            }
            if (sensors != null) {
                val vm: SensorsViewModel by viewModels()
                vm.update(sensors)
                Log.d(TAG, "received new sensor state $sensors")
            }
        }
    }
    companion object {
        private val TAG = BikeActivity::class.java.simpleName
    }
}