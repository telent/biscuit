package idv.markkuo.cscblebridge

import android.app.ActivityManager
import android.content.*
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import idv.markkuo.cscblebridge.CSCService.LocalBinder
import java.time.Instant
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
    private var mService: CSCService? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = intent
        val mServiceIntent = Intent(applicationContext, CSCService::class.java)
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
        if (mBound) {
            Log.d(TAG, "Service already started")
        } else {
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
        val mServiceIntent = Intent(applicationContext, CSCService::class.java)
        super.onResume()
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

    private val isServiceRunning: Boolean
        private get() {
            val manager = (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                if (CSCService::class.java.name == service.service.className) {
                    return true
                }
            }
            return false
        }

    // this BroadcastReceiver is used to update UI only
    private inner class MainActivityReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val statusBSD = intent.getStringExtra("bsd_service_status") // bicycle speed
            val statusBC = intent.getStringExtra("bc_service_status") // bicycle cadence
            //            final String statusHR = intent.getStringExtra("hr_service_status");
//            final String statusRsc = intent.getStringExtra("ss_service_status");
//            final long speedTimestamp = intent.getLongExtra("speed_timestamp", -1);
//            final long cadenceTimestamp = intent.getLongExtra("cadence_timestamp", -1);
//            final long hrTimestamp = intent.getLongExtra("hr_timestamp", -1);
//            final long runTimestamp = intent.getLongExtra("ss_stride_count_timestamp", -1);
            val speed = intent.getFloatExtra("speed", -1.0f)
            val cadence = intent.getIntExtra("cadence", -1)
            val hr = intent.getIntExtra("hr", -1)
            //            final float runSpeed = intent.getFloatExtra("ss_speed", -1);
//            final long runStrideCount = intent.getLongExtra("ss_stride_count", -1);
            val instant = Instant.now() // Current moment in UTC.
            val now = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
            runOnUiThread {
                if (statusBSD != null) tv_speed!!.text = statusBSD else if (speed >= 0.0f) tv_speed!!.text = String.format("%.01f", speed) + " km/h"
                if (statusBC != null) tv_speed!!.text = statusBC else if (cadence >= 0) tv_cadence!!.text = String.format("%3d rpm", cadence)
                if (now != null) tv_time!!.text = String.format("%2d:%02d:%02d",
                        now.hour, now.minute, now.second)
            }
        }
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }
}