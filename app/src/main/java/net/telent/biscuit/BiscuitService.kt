package net.telent.biscuit

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.media.app.NotificationCompat
import kotlinx.parcelize.Parcelize
import java.time.Duration
import java.time.Instant
import java.util.*

data class Sensors(
        val speed : SpeedSensor,
        var cadence : CadenceSensor,
        var stride : StrideSensor,
        var heart: HeartSensor,
        var position: PositionSensor
) {
    fun asList(): List<ISensor> {
        return listOf(speed, cadence, heart, stride, position)
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
        if (speed.isCombinedSensor && speed.state == ISensor.SensorState.PRESENT && cadence.state == ISensor.SensorState.ABSENT) {
            Log.d("sensors", "combined speed ${speed.antDeviceNumber}")
            cadence.startSearch(context, speed.antDeviceNumber!!)
        }
        if (cadence.isCombinedSensor && cadence.state == ISensor.SensorState.PRESENT && speed.state == ISensor.SensorState.ABSENT) {
            Log.d("sensors", "combined cadence ${cadence.antDeviceNumber}")
            speed.startSearch(context, cadence.antDeviceNumber!!)
        }
    }
}

@Parcelize data class SensorSummaries(val entries: List<SensorSummary>) : Parcelable

class BiscuitService : Service() {
    private fun reportSensorStatuses() {
        val payload = SensorSummaries(sensors.asList().map { s -> s.stateReport() })

        Intent(INTENT_NAME).let {
            it.putExtra("sensor_state", payload)
            sendBroadcast(it)
        }
        sensors.reconnectIfCombined(this)
    }

    private var sensors = Sensors(
            speed = SpeedSensor { s  -> reportSensorStatuses() },
            cadence = CadenceSensor { s  -> reportSensorStatuses() },
            stride = StrideSensor { s  -> reportSensorStatuses() },
            heart = HeartSensor { s  -> reportSensorStatuses() },
            position = PositionSensor { s  -> reportSensorStatuses() })

    private var movingTime: Duration = Duration.ZERO

    // Binder for activities wishing to communicate with this service
    private val binder: IBinder = LocalBinder()

    private val db by lazy {
        BiscuitDatabase.getInstance(this.applicationContext)
    }

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
                .setContentText("Recording track")
                .setContentIntent(notifyPendingIntent)
                .setSmallIcon(R.drawable.ic_chaindodger)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                .addAction(R.drawable.ic_baseline_stop_24, "STOP", stopServicePendingIntent)
                .setAutoCancel(true)
                .setStyle(NotificationCompat.MediaStyle().setShowActionsInCompactView(0))
                .build()
        when {
            intent.hasExtra("stop_service") -> {
                Log.d(TAG, "stopping")
                Toast.makeText(this, "Stopped recording", Toast.LENGTH_SHORT).show()
                shutdown()
            }
            intent.hasExtra("refresh_sensors") -> {
                sensors.startSearch(this)
            }
            else -> {
                startForeground(ONGOING_NOTIFICATION_ID, notification)
            }
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

    private val updaterThread = object: Thread() {
        var previousUpdateTime = Instant.EPOCH
        var amRunning = true
        fun shutdown() {
            amRunning = false
        }
        override fun run() {
            var previousLatitude : Double? = null
            var previousLongitude : Double? = null

            db.sessionDao().start(Instant.now())
            while (amRunning) {
                val latest = sensors.timestamp()
                if (latest > previousUpdateTime) {
                    if (sensors.speed.speed > 1.0 && previousUpdateTime > Instant.EPOCH) {
                        val elapsed = Duration.between(previousUpdateTime, latest)
                        movingTime = movingTime.plus(elapsed)
                    }
                    if (sensors.speed.speed > 0.0 ||
                            sensors.position.latitude != previousLatitude ||
                            sensors.position.longitude != previousLongitude) {
                        Log.d("ggg",
                                "lat ${sensors.position.latitude} $previousLatitude lng ${sensors.position.longitude} $previousLongitude")
                        logUpdate()
                    }
                    previousUpdateTime = latest
                    previousLatitude = sensors.position.latitude
                    previousLongitude = sensors.position.longitude
                }
                sleep(200)
            }
        }
    }

    override fun onCreate() {
        Log.d(TAG, "Service started $INTENT_NAME")
        super.onCreate()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Not recording track, no Location permission",
                    Toast.LENGTH_SHORT).show()
            sensors.position.state = ISensor.SensorState.FORBIDDEN
        }
        sensors.startSearch(this)
        updaterThread.start()
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        Log.d(TAG, "onTaskRemoved called")
        super.onTaskRemoved(rootIntent)
        shutdown()
    }

    private fun shutdown() {
        stopForeground(true)
        stopSelf()
        sensors.close()
        updaterThread.shutdown()
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
        sensors.close()
        updaterThread.shutdown()
    }

    private fun logUpdate() {
        val tp = Trackpoint(
                timestamp = Instant.now(),
                lng = sensors.position.longitude,
                lat = sensors.position.latitude,
                speed = sensors.speed.speed.toFloat(),
                cadence = sensors.cadence.cadence.toFloat(),
                movingTime = movingTime,
                distance = sensors.speed.distance.toFloat()
        )
        db.trackpointDao().addPoint(tp)
        Log.d(TAG, "recording: $tp")

        val i = Intent(INTENT_NAME)
        i.putExtra("trackpoint", tp)
        sendBroadcast(i)
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