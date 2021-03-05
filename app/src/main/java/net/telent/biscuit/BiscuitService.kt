package net.telent.biscuit

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.media.app.NotificationCompat
import kotlinx.parcelize.Parcelize
import java.lang.Thread.sleep
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.concurrent.thread

data class Sensors(
        val speed : SpeedSensor,
        var cadence : CadenceSensor,
        var stride : StrideSensor,
        var heart: HeartSensor
) {
    private fun asList(): List<ISensor> {
        return listOf(speed, cadence, heart, stride)
    }

    fun close() {
        asList().map { it.close() }
    }

    fun startSearch(context: Context) {
         asList().map { it.startSearch(context, 0) }
    }

    fun timestamp(): Instant {
        return asList().map { s -> s.timestamp }.reduce { a, b -> if(a > b) a else b}
    }

    fun reconnectIfCombined(context: Context) {
        if (speed.isCombinedSensor && speed.state == Sensor.SensorState.PRESENT && cadence.state == Sensor.SensorState.ABSENT) {
            Log.d("sensors", "combined speed ${speed.antDeviceNumber}")
            cadence.startSearch(context, speed.antDeviceNumber!!)
        }
        if (cadence.isCombinedSensor && cadence.state == Sensor.SensorState.PRESENT && speed.state == Sensor.SensorState.ABSENT) {
            Log.d("sensors", "combined cadence ${cadence.antDeviceNumber}")
            speed.startSearch(context, cadence.antDeviceNumber!!)
        }
    }
}

@Parcelize data class SensorSummaries(
        val speed : SensorSummary,
        val cadence: SensorSummary,
        val stride: SensorSummary,
        val heart : SensorSummary) : Parcelable

class BiscuitService : Service() {
    private fun reportSensorStatuses() {
        val payload = SensorSummaries(
                sensors.speed.stateReport(),
                sensors.cadence.stateReport(),
                sensors.stride.stateReport(),
                sensors.heart.stateReport())
        Log.d(TAG, "reporting sensors state $payload")
        val i = Intent(INTENT_NAME)
        i.putExtra("sensor_state", payload)
        sendBroadcast(i)
        sensors.reconnectIfCombined(this)
    }

    private var sensors = Sensors(
            speed = SpeedSensor { s  -> reportSensorStatuses() },
            cadence = CadenceSensor { s  -> reportSensorStatuses() },
            stride = StrideSensor { s  -> reportSensorStatuses() },
            heart = HeartSensor { s  -> reportSensorStatuses() })

    private var movingTime: Duration = Duration.ZERO

    private var lastLocation : Location? = null

    // for onCreate() failure case
    private var antInitialized = false

    // Binder for activities wishing to communicate with this service
    private val binder: IBinder = LocalBinder()

    private val db by lazy {
        BiscuitDatabase.getInstance(this.applicationContext)
    }

    private var lastGpsUpdate : Instant = Instant.EPOCH

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand$intent")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(CHANNEL_DEFAULT_IMPORTANCE, MAIN_CHANNEL_NAME)
        }
        val notifyPendingIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this.applicationContext, BikeActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT)
        val stopServiceIntent = Intent(this, BiscuitService::class.java)
        stopServiceIntent.putExtra("stop_service", 1)
        val stopServicePendingIntent = PendingIntent.getService(
                this,
                0,
                stopServiceIntent,
                0)
        val notification = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_DEFAULT_IMPORTANCE)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Active")
                .setContentIntent(notifyPendingIntent)
                .setSmallIcon(R.drawable.ic_chaindodger)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                .addAction(R.drawable.ic_baseline_stop_24, "STOP SERVICE", stopServicePendingIntent)
                .setAutoCancel(true)
                .setStyle(NotificationCompat.MediaStyle().setShowActionsInCompactView(0))
                .build()
        if (intent.hasExtra("stop_service")) {
            Log.d(TAG, "stopping")
            Toast.makeText(this, "Stopped recording", Toast.LENGTH_SHORT).show()
            stopForeground(true)
            stopSelf()
            cleanupAnt()
        } else if(intent.hasExtra("refresh_sensors")) {
            initAntPlus()
        } else {
            startForeground(ONGOING_NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(channelId: String, channelName: String) {
        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
        channel.lightColor = Color.BLUE
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private val updaterThread = thread(start = false) {
        var previousUpdateTime = Instant.EPOCH
        db.sessionDao().start(Instant.now())
        while (antInitialized) {
            val latest = maxOf(lastGpsUpdate, sensors.timestamp())
            if (latest > previousUpdateTime) {
                if (sensors.speed.speed > 1.0 && previousUpdateTime > Instant.EPOCH) {
                    val elapsed = Duration.between(previousUpdateTime, latest)
                    movingTime = movingTime.plus(elapsed)
                }
                logUpdate(true)
                previousUpdateTime = latest
            }
            sleep(200)
        }
    }
    private lateinit var locationManager: LocationManager

    override fun onCreate() {
        Log.d(TAG, "Service started $INTENT_NAME")
        super.onCreate()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Not recording track, no Location permission",
                    Toast.LENGTH_SHORT).show()
        } else {
            locationManager = this.getSystemService(LOCATION_SERVICE) as LocationManager
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            lastLocation = location
            requestLocationUpdates()
        }
        initAntPlus()
        updaterThread.start()
    }

    @SuppressLint("MissingPermission") // only called from fns that check the permission
    private fun requestLocationUpdates() {
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000L,1.0f,
                object: LocationListener {
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                        Log.d(TAG, "status changed $provider $status,$extras")
                    }
                    override fun onProviderDisabled(provider: String) {
                        Log.d(TAG, "location provider $provider disabled")
                    }
                    override fun onProviderEnabled(provider: String) {
                        Log.d(TAG, "location provider $provider enabled")
                    }
                    override fun onLocationChanged(loc: Location) {
                        lastLocation = loc
                        lastGpsUpdate = Instant.ofEpochMilli(loc.time)
                        Log.d(TAG, "locationChanged $loc")
                    }
                })
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        Log.d(TAG, "onTaskRemoved called")
        super.onTaskRemoved(rootIntent)
        stopForeground(true)
        stopSelf()
        cleanupAnt()
    }

    private fun cleanupAnt() {
        sensors.close()
        antInitialized = false
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
        cleanupAnt()
    }


    private fun logUpdate(writeDatabase : Boolean) {
        val tp = Trackpoint(
                timestamp = Instant.now(),
                lng = lastLocation?.longitude,
                lat = lastLocation?.latitude,
                speed = sensors.speed.speed.toFloat(),
                cadence = sensors.cadence.cadence.toFloat(),
                movingTime = movingTime,
                wheelRevolutions = (sensors.speed.distance / 2.2).toLong()
        )
        if(writeDatabase) {
            db.trackpointDao().addPoint(tp)
            Log.d(TAG, "recording: $tp")
        }

        val i = Intent(INTENT_NAME)
        i.putExtra("trackpoint", tp)
        sendBroadcast(i)
    }

    /**
     * Initialize searching for all supported sensors
     */
    private fun initAntPlus() {
        sensors.startSearch(this)
        antInitialized = true
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    /**
     * Get the services for communicating with it
     */
    inner class LocalBinder : Binder() {
        val service: BiscuitService
            get() = this@BiscuitService
    }

    companion object {
        private val TAG = BiscuitService::class.java.simpleName
        const val INTENT_NAME = BuildConfig.APPLICATION_ID + ".TRACKPOINTS"
        private const val ONGOING_NOTIFICATION_ID = 9999
        private const val CHANNEL_DEFAULT_IMPORTANCE = "csc_ble_channel"
        private const val MAIN_CHANNEL_NAME = "CscService"

    }
}