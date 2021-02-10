package net.telent.biscuit

import android.Manifest
import android.app.ActivityManager
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import net.telent.biscuit.BiscuitService.LocalBinder
import java.time.Instant.EPOCH
import java.time.LocalDateTime
import java.time.ZoneId

class MainActivity : AppCompatActivity() {
    private val tv_speedSensorState: TextView? = null
    private val tv_cadenceSensorState: TextView? = null
    private val tv_hrSensorState: TextView? = null
    private val tv_runSensorState: TextView? = null
    private val tv_speedSensorTimestamp: TextView? = null
    private val tv_cadenceSensorTimestamp: TextView? = null
    private val tv_hrSensorTimestamp: TextView? = null
    private val tv_runSensorTimestamp: TextView? = null
    private var tv_speed: TextView? = null
    private var tv_cadence: TextView? = null
    private val tv_hr: TextView? = null
    private val tv_runSpeed: TextView? = null
    private val tv_runCadence: TextView? = null
    private var tv_time: TextView? = null
    private var btn_service: Button? = null
    private var receiver: MainActivityReceiver? = null
    private var mBound = false
    private var mService: BiscuitService? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mServiceIntent = Intent(applicationContext, BiscuitService::class.java)
        setContentView(R.layout.activity_main)
        tv_speed = findViewById(R.id.SpeedText)
        tv_cadence = findViewById(R.id.CadenceText)
        tv_time = findViewById(R.id.TimeText)
        ensureServiceRunning(mServiceIntent)
        receiver = MainActivityReceiver()
        // register intent from our service
        val filter = IntentFilter()
        filter.addAction("idv.markkuo.cscblebridge.ANTDATA")
        registerReceiver(receiver, filter)
    }

    private fun ensureServiceRunning(mServiceIntent: Intent) {
        if (!mBound) {
            Log.d(TAG, "Starting Service")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(mServiceIntent) else startService(mServiceIntent)

            // Bind to the service so we can interact with it
            if (!bindService(mServiceIntent, connection, Context.BIND_AUTO_CREATE)) {
                Log.d(TAG, "Failed to bind to service")
            } else {
                mBound = true
            }
        }
    }

    override fun onResume() {
        val mServiceIntent = Intent(applicationContext, BiscuitService::class.java)
        super.onResume()
        ensureLocationPermission(1)
        ensureServiceRunning(mServiceIntent)
    }

    /** Defines callbacks for service binding, passed to bindService()  */
    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as LocalBinder
            mService = binder.service
            mBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }

    // Unbind from the service
    fun unbindService() {
        if (mBound) {
            unbindService(connection)
            mBound = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
        unbindService()
    }

    private fun resetUi() {
        tv_speed!!.text = getText(R.string.no_data)
        tv_cadence!!.text = getText(R.string.no_data)
        tv_time!!.text = "--:--"
    }

    fun ensureLocationPermission(locationRequestCode : Int) {
        if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                            Manifest.permission.ACCESS_FINE_LOCATION)) {
                ActivityCompat.requestPermissions(this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            } else {
                ActivityCompat.requestPermissions(this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1);
            }
        }
    }

    private val isServiceRunning: Boolean
        get() {
            val manager = (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                if (BiscuitService::class.java.name == service.service.className) {
                    return true
                }
            }
            return false
        }

    private inner class MainActivityReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val statusBSD = intent.getStringExtra("bsd_service_status") // bicycle speed
            val statusBC = intent.getStringExtra("bc_service_status") // bicycle cadence
            val trackpoint = intent.getParcelableExtra<Trackpoint>("trackpoint") ?: Trackpoint(EPOCH, null,null)
            val instant = trackpoint.timestamp
            val speed = trackpoint.speed
            val cadence = trackpoint.cadence
            val now = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
            runOnUiThread {
                if (statusBSD != null) tv_speed!!.text = statusBSD else if (speed >= 0.0f) tv_speed!!.text = String.format("%.01f", speed) + " km/h"
                if (statusBC != null) tv_speed!!.text = statusBC else if (cadence >= 0.0f) tv_cadence!!.text = String.format("%3.1f rpm", cadence)
                if (now != null) tv_time!!.text = String.format("%2d:%02d:%02d",
                        now.hour, now.minute, now.second)
            }
        }
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }
}