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
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.util.Pair
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.media.app.NotificationCompat
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikeCadencePcc
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikeSpeedDistancePcc
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikeSpeedDistancePcc.CalculatedSpeedReceiver
import com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc
import com.dsi.ant.plugins.antplus.pcc.AntPlusStrideSdmPcc
import com.dsi.ant.plugins.antplus.pcc.AntPlusStrideSdmPcc.IStrideCountReceiver
import com.dsi.ant.plugins.antplus.pcc.defines.DeviceState
import com.dsi.ant.plugins.antplus.pcc.defines.EventFlag
import com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc.IDeviceStateChangeReceiver
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc.IPluginAccessResultReceiver
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle
import java.lang.Thread.sleep
import java.math.BigDecimal
import java.time.Instant
import java.util.*
import java.util.concurrent.Semaphore
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.sin

class BiscuitService : Service() {
    // Ant+ sensors
    private var bsdPcc: AntPlusBikeSpeedDistancePcc? = null
    private var bsdReleaseHandle: PccReleaseHandle<AntPlusBikeSpeedDistancePcc>? = null
    private var bcPcc: AntPlusBikeCadencePcc? = null
    private var bcReleaseHandle: PccReleaseHandle<AntPlusBikeCadencePcc>? = null
    private var hrPcc: AntPlusHeartRatePcc? = null
    private var hrReleaseHandle: PccReleaseHandle<AntPlusHeartRatePcc>? = null
    private var ssPcc: AntPlusStrideSdmPcc? = null
    private var ssReleaseHandle: PccReleaseHandle<AntPlusStrideSdmPcc>? = null

    // last wheel and crank (speed/cadence) information to send to CSCProfile
    private var cumulativeWheelRevolution: Long = 0
    private var cumulativeCrankRevolution: Long = 0
    private var lastWheelEventTime = 0
    private var lastCrankEventTime = 0
    private var lastSpeedTimestamp: Long = 0
    private var lastCadenceTimestamp: Long = 0
    private var lastHRTimestamp: Long = 0
    private var lastSSDistanceTimestamp: Long = 0
    private var lastSSStrideCountTimestamp: Long = 0
    private var lastSpeed = 0f
    private var lastCadence = 0
    private var lastHR = 0
    private var lastSSDistance: Long = 0
    private var lastSSSpeed = 0f
    private var lastStridePerMinute: Long = 0
    private var movingTime: Long = 0

    private var lastLocation : Location? = null

    // for onCreate() failure case
    private var antInitialized = false

    fun isFake() : Boolean {
        return(FLAVOR == "fake")
    }

    // Used to flag if we have a combined speed and cadence sensor and have already re-connected as combined
    private var combinedSensorConnected = false

    // Binder for activities wishing to communicate with this service
    private val binder: IBinder = LocalBinder()
    private val mBSDResultReceiver: IPluginAccessResultReceiver<AntPlusBikeSpeedDistancePcc> = object : IPluginAccessResultReceiver<AntPlusBikeSpeedDistancePcc> {
        override fun onResultReceived(result: AntPlusBikeSpeedDistancePcc?,
                                      resultCode: RequestAccessResult?, initialDeviceState: DeviceState?) {
            if(this@BiscuitService.isFake()) {
                sendDeviceState("bsd_service_status", DeviceState.TRACKING,
                        RequestAccessResult.SUCCESS)
                return
            }
            if (resultCode == RequestAccessResult.SUCCESS) {
                bsdPcc = result
                if(result != null) Log.d(TAG, result.deviceName + ": " + initialDeviceState)
                subscribeToEvents()
            } else if (resultCode == RequestAccessResult.USER_CANCELLED) {
                Log.d(TAG, "BSD Closed:$resultCode")
            } else {
                Log.w(TAG, "BSD state changed:$initialDeviceState, resultCode:$resultCode")
            }
            // send broadcast
            sendDeviceState("bsd_service_status", initialDeviceState, resultCode)
        }

        private fun subscribeToEvents() {
            bsdPcc!!.subscribeCalculatedSpeedEvent(object : CalculatedSpeedReceiver(circumference) {
                override fun onNewCalculatedSpeed(estTimestamp: Long,
                                                  eventFlags: EnumSet<EventFlag>, calculatedSpeed: BigDecimal) {
                    lastSpeed = (calculatedSpeed.multiply(msToKmSRatio)).toFloat()
                }
            })
            bsdPcc!!.subscribeRawSpeedAndDistanceDataEvent { estTimestamp, _eventFlags, timestampOfLastEvent, cumulativeRevolutions -> //estTimestamp - The estimated timestamp of when this event was triggered. Useful for correlating multiple events and determining when data was sent for more accurate data records.
                //eventFlags - Informational flags about the event.
                //timestampOfLastEvent - Sensor reported time counter value of last distance or speed computation (up to 1/200s accuracy). Units: s. Rollover: Every ~46 quadrillion s (~1.5 billion years).
                //cumulativeRevolutions - Total number of revolutions since the sensor was first connected. Note: If the subscriber is not the first PCC connected to the device the accumulation will probably already be at a value greater than 0 and the subscriber should save the first received value as a relative zero for itself. Units: revolutions. Rollover: Every ~9 quintillion revolutions.
                Log.v(TAG, "=> BSD: Cumulative revolution:$cumulativeRevolutions, lastEventTime:$timestampOfLastEvent")
                cumulativeWheelRevolution = cumulativeRevolutions
                lastWheelEventTime = ((timestampOfLastEvent.toInt().toDouble() * 1024.0).toInt())
                lastSpeedTimestamp = estTimestamp
                Log.d(TAG,"wheel timestamp $estTimestamp")
                lastUpdateTime = Instant.ofEpochMilli(estTimestamp)
            }
            if (bsdPcc!!.isSpeedAndCadenceCombinedSensor && !combinedSensorConnected) {
                // reconnect cadence sensor as combined sensor
                if (bcReleaseHandle != null) {
                    bcReleaseHandle!!.close()
                }
                combinedSensorConnected = true
                bcReleaseHandle = AntPlusBikeCadencePcc.requestAccess(applicationContext, bsdPcc!!.antDeviceNumber, 0, true,
                        mBCResultReceiver, mBCDeviceStateChangeReceiver)
            }
        }
    }

    private fun sendDeviceState(name: String, initialDeviceState: DeviceState?, resultCode: RequestAccessResult?) {
        val i = Intent(INTENT_NAME)
        i.putExtra(name, "$initialDeviceState - $resultCode")
        sendBroadcast(i)
    }

    private fun sendDeviceSearching(name: String) {
        val i = Intent(INTENT_NAME)
        i.putExtra(name, "Starting search")
        sendBroadcast(i)
    }

    private val mBCResultReceiver: IPluginAccessResultReceiver<AntPlusBikeCadencePcc> = object : IPluginAccessResultReceiver<AntPlusBikeCadencePcc> {
        // Handle the result, connecting to events on success or reporting
        // failure to user.
        override fun onResultReceived(result: AntPlusBikeCadencePcc?,
                                      resultCode: RequestAccessResult?, initialDeviceState: DeviceState?) {
            if(this@BiscuitService.isFake()) {
                sendDeviceState("bc_service_status", DeviceState.TRACKING,
                         RequestAccessResult.SUCCESS)
                return
            }
            if (resultCode == RequestAccessResult.SUCCESS) {
                bcPcc = result
                if(result != null) Log.d(TAG, result.deviceName + ": " + initialDeviceState)
                subscribeToEvents()
            } else if (resultCode == RequestAccessResult.USER_CANCELLED) {
                Log.d(TAG, "BC Closed:$resultCode")
            } else {
                Log.w(TAG, "BC state changed:$initialDeviceState, resultCode:$resultCode")
            }
            sendDeviceState("bc_service_status", initialDeviceState, resultCode)
        }

        private fun subscribeToEvents() {
            bcPcc!!.subscribeCalculatedCadenceEvent { estTimestamp, eventFlags, calculatedCadence -> //Log.v(TAG, "Cadence:" + calculatedCadence.intValue());
                lastCadence = calculatedCadence.toInt()
            }
            bcPcc!!.subscribeRawCadenceDataEvent { estTimestamp, eventFlags, timestampOfLastEvent, cumulativeRevolutions ->
                Log.v(TAG, "=> BC: Cumulative revolution:$cumulativeRevolutions, lastEventTime:$timestampOfLastEvent")
                cumulativeCrankRevolution = cumulativeRevolutions
                lastCrankEventTime = (timestampOfLastEvent.toDouble() * 1024.0).toInt()
                lastCadenceTimestamp = estTimestamp
                lastUpdateTime = Instant.ofEpochMilli(estTimestamp)
            }
            if (bcPcc!!.isSpeedAndCadenceCombinedSensor && !combinedSensorConnected) {
                // reconnect speed sensor as a combined sensor
                if (bsdReleaseHandle != null) {
                    bsdReleaseHandle!!.close()
                }
                combinedSensorConnected = true
                bsdReleaseHandle = AntPlusBikeSpeedDistancePcc.requestAccess(applicationContext, bcPcc!!.antDeviceNumber, 0, true,
                        mBSDResultReceiver, mBSDDeviceStateChangeReceiver)
            }
        }
    }
    private val mHRResultReceiver: IPluginAccessResultReceiver<AntPlusHeartRatePcc> = object : IPluginAccessResultReceiver<AntPlusHeartRatePcc> {
        override fun onResultReceived(result: AntPlusHeartRatePcc?, resultCode: RequestAccessResult?, initialDeviceState: DeviceState?) {
            if (resultCode == RequestAccessResult.SUCCESS) {
                hrPcc = result
                if(result != null) Log.d(TAG, result.deviceName + ": " + initialDeviceState)
                subscribeToEvents()
            } else if (resultCode == RequestAccessResult.USER_CANCELLED) {
                Log.d(TAG, "HR Closed:$resultCode")
            } else {
                Log.w(TAG, "HR state changed:$initialDeviceState, resultCode:$resultCode")
            }
            sendDeviceState("hr_service_status", initialDeviceState, resultCode)
        }

        private fun subscribeToEvents() {
            hrPcc!!.subscribeHeartRateDataEvent { estTimestamp, eventFlags, computedHeartRate, heartBeatCount, heartBeatEventTime, dataState ->
                lastHR = computedHeartRate
                lastHRTimestamp = estTimestamp
                lastUpdateTime = Instant.ofEpochMilli(estTimestamp)
            }
        }
    }
    private val mSSResultReceiver: IPluginAccessResultReceiver<AntPlusStrideSdmPcc> = object : IPluginAccessResultReceiver<AntPlusStrideSdmPcc> {
        override fun onResultReceived(result: AntPlusStrideSdmPcc?, resultCode: RequestAccessResult?, initialDeviceState: DeviceState?) {
            if (resultCode == RequestAccessResult.SUCCESS) {
                ssPcc = result
                Log.d(TAG, (if (result != null) result.deviceName else "(null)" ) + ": " + initialDeviceState)
                subscribeToEvents()
            } else if (resultCode == RequestAccessResult.USER_CANCELLED) {
                Log.d(TAG, "SS Closed:$resultCode")
            } else {
                Log.w(TAG, "SS state changed: $initialDeviceState, resultCode:$resultCode")
            }
            sendDeviceState("ss_service_status", initialDeviceState, resultCode)
        }

        private fun subscribeToEvents() {
            // https://www.thisisant.com/developer/ant-plus/device-profiles#528_tab
            ssPcc!!.subscribeStrideCountEvent(object : IStrideCountReceiver {
                private val strideList = LinkedList<Pair<Long, Long>>()
                private val lock = Semaphore(1)
                override fun onNewStrideCount(estTimestamp: Long, eventFlags: EnumSet<EventFlag>, cumulativeStrides: Long) {
                    Thread {
                        val FALLBACK_MAX_LIST_SIZE = 500
                        try {
                            lock.acquire()
                            // Calculate number of strides per minute, updates happen around every 500 ms, this number
                            // may be off by that amount but it isn't too significant
                            strideList.addFirst(Pair(estTimestamp, cumulativeStrides))
                            var strideCount: Long = 0
                            var valueFound = false
                            var i = 0
                            for (p in strideList) {
                                // Cadence over the last 10 seconds
                                if (estTimestamp - p.first >= 10_000) {
                                    valueFound = true
                                    strideCount = calculateStepsPerMin(estTimestamp, cumulativeStrides, p)
                                    break
                                } else if (i + 1 == strideList.size) {
                                    // No value was found yet, it has not been 10 seconds. Give an early rough estimate
                                    strideCount = calculateStepsPerMin(estTimestamp, cumulativeStrides, p)
                                }
                                i++
                            }
                            while (valueFound && strideList.size >= i + 1 || strideList.size > FALLBACK_MAX_LIST_SIZE) {
                                strideList.removeLast()
                            }
                            lastSSStrideCountTimestamp = estTimestamp
                            lastStridePerMinute = strideCount
                            lastUpdateTime = Instant.ofEpochMilli(estTimestamp)
                            lock.release()
                        } catch (e: InterruptedException) {
                            Log.e(TAG, "Unable to acquire lock to update running cadence", e)
                        }
                    }.start()
                }

                private fun calculateStepsPerMin(estTimestamp: Long, cumulativeStrides: Long, p: Pair<Long, Long>): Long {
                    val elapsedTimeMs = estTimestamp - p.first.toFloat()
                    return if (elapsedTimeMs == 0f) {
                        0
                    } else ((cumulativeStrides - p.second) * (60_000 / elapsedTimeMs)) as Long
                }
            })
            ssPcc!!.subscribeDistanceEvent { estTimestamp, eventFlags, distance ->
                lastSSDistanceTimestamp = estTimestamp
                lastSSDistance = distance.toLong()
                lastUpdateTime = Instant.ofEpochMilli(estTimestamp)
            }
            ssPcc!!.subscribeInstantaneousSpeedEvent { estTimestamp, eventFlags, instantaneousSpeed ->
                lastSSDistanceTimestamp = estTimestamp
                lastSSSpeed = instantaneousSpeed.toFloat()
                lastUpdateTime = Instant.ofEpochMilli(estTimestamp)
            }
        }
    }

    private enum class AntSensorType {
        CyclingSpeed, CyclingCadence, HR, StrideBasedSpeedAndDistance
    }

    private inner class AntDeviceChangeReceiver(private val type: AntSensorType) : IDeviceStateChangeReceiver {
        override fun onDeviceStateChange(newDeviceState: DeviceState) {
            var extraName = "unknown"
            if (type == AntSensorType.CyclingSpeed) {
                extraName = "bsd_service_status"
                Log.d(TAG, "Speed sensor onDeviceStateChange:$newDeviceState")
            } else if (type == AntSensorType.CyclingCadence) {
                extraName = "bc_service_status"
                Log.d(TAG, "Cadence sensor onDeviceStateChange:$newDeviceState")
            } else if (type == AntSensorType.HR) {
                extraName = "hr_service_status"
                Log.d(TAG, "HR sensor onDeviceStateChange:$newDeviceState")
            } else if (type == AntSensorType.StrideBasedSpeedAndDistance) {
                extraName = "ss_service_status"
                Log.d(TAG, "Stride based speed and distance onDeviceStateChange:$newDeviceState")
            }
            sendDeviceState(extraName, newDeviceState, null)

            // if the device is dead (closed)
            if (newDeviceState == DeviceState.DEAD) {
                bsdPcc = null
            }
        }
    }

    private val mBSDDeviceStateChangeReceiver: IDeviceStateChangeReceiver = AntDeviceChangeReceiver(AntSensorType.CyclingSpeed)
    private val mBCDeviceStateChangeReceiver: IDeviceStateChangeReceiver = AntDeviceChangeReceiver(AntSensorType.CyclingCadence)
    private val mHRDeviceStateChangeReceiver: IDeviceStateChangeReceiver = AntDeviceChangeReceiver(AntSensorType.HR)
    private val mSSDeviceStateChangeReceiver: IDeviceStateChangeReceiver = AntDeviceChangeReceiver(AntSensorType.StrideBasedSpeedAndDistance)

    private val db by lazy {
        BiscuitDatabase.getInstance(this.applicationContext)
    }

    private var lastUpdateTime : Instant = Instant.EPOCH

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
                .setSmallIcon(R.drawable.ic_notification_icon)
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

    private val updaterThread = thread(start = false){
        var previousUpdateTime = Instant.EPOCH
        while(antInitialized) {
            val lastut = lastUpdateTime
            val isChanged = lastut > previousUpdateTime

            if(lastSpeed > 1.0f && previousUpdateTime > Instant.EPOCH) {
                val elapsed = (lastut.toEpochMilli() - previousUpdateTime.toEpochMilli())
                Log.d(TAG, "elapsed $elapsed")
                movingTime += elapsed
            }

            logUpdate(isChanged)
            sleep(200)
            previousUpdateTime = lastut
        }
    }

    private val fakeSensorThread = thread(start = false) {
        while (antInitialized) {
            sleep(40)
            val now = Instant.now()
            val speed = 30 * sin(now.toEpochMilli().toDouble() / 12000.0) - 1
            if(lastSpeed > 0 || speed >= 0) {
                synchronized(this) {
                    lastSpeed = max(speed, 0.0).toFloat()
                    if (lastSpeed > 1.0f) {
                        cumulativeWheelRevolution += 1
                        lastCadence = if (Math.random() > 0.3) lastSpeed.toInt() else 0
                    } else {
                        lastCadence = 0
                    }
                    lastUpdateTime = now
                }
            }
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
        // ANT+
        initAntPlus()
        if(this.isFake()) fakeSensorThread.start()
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
                        lastUpdateTime = Instant.ofEpochMilli(loc.time)
                        Log.d(TAG, "" + loc)
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
        if (antInitialized) {
            antInitialized = false
            // stop ANT+
            if (bsdReleaseHandle != null) bsdReleaseHandle!!.close()
            if (bcReleaseHandle != null) bcReleaseHandle!!.close()
            if (hrReleaseHandle != null) hrReleaseHandle!!.close()
            if (ssReleaseHandle != null) ssReleaseHandle!!.close()
            combinedSensorConnected = false
        }
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
                speed = lastSpeed,
                cadence = lastCadence.toFloat(),
                movingTime = movingTime,
                wheelRevolutions = cumulativeWheelRevolution
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
        if(this.isFake())
            Log.d(TAG, "faking ANT+ access")
        else
            Log.d(TAG, "requesting ANT+ access")
        startSpeedSensorSearch()
        startCadenceSensorSearch()
        startHRSensorSearch()
        startStrideSdmSensorSearch()
        antInitialized = true
    }

    /**
     * Initializes the speed sensor search
     */
    private fun startSpeedSensorSearch() {
        //Release the old access if it exists
        if (bsdReleaseHandle != null) bsdReleaseHandle!!.close()
        combinedSensorConnected = false

        // starts speed sensor search
        bsdReleaseHandle = AntPlusBikeSpeedDistancePcc.requestAccess(this, 0, 0, false,
                mBSDResultReceiver, mBSDDeviceStateChangeReceiver)

        sendDeviceSearching("bsd_service_status")
    }

    /**
     * Initializes the cadence sensor search
     */
    private fun startCadenceSensorSearch() {
        //Release the old access if it exists
        if (bcReleaseHandle != null) bcReleaseHandle!!.close()

        // starts cadence sensor search
        bcReleaseHandle = AntPlusBikeCadencePcc.requestAccess(this, 0, 0, false,
                mBCResultReceiver, mBCDeviceStateChangeReceiver)

        // send initial state for UI
        sendDeviceSearching("bc_service_status")
    }

    /**
     * Initializes the HR  sensor search
     */
    private fun startHRSensorSearch() {
        //Release the old access if it exists
        if (hrReleaseHandle != null) hrReleaseHandle!!.close()

        // starts hr sensor search
        hrReleaseHandle = AntPlusHeartRatePcc.requestAccess(this, 0, 0,
                mHRResultReceiver, mHRDeviceStateChangeReceiver)

        sendDeviceSearching("hr_service_status")
    }

    /**
     * Initialized the Stride SDM (Stride based Speed and Distance Monitor) sensor search
     *
     * ex. Garmin Foot Pod
     */
    private fun startStrideSdmSensorSearch() {
        if (ssReleaseHandle != null) ssReleaseHandle!!.close()
        ssReleaseHandle = AntPlusStrideSdmPcc.requestAccess(this, 0, 0,
                mSSResultReceiver, mSSDeviceStateChangeReceiver)
        sendDeviceSearching("ss_service_status")
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
        const val FLAVOR = BuildConfig.FLAVOR
        private const val ONGOING_NOTIFICATION_ID = 9999
        private const val CHANNEL_DEFAULT_IMPORTANCE = "csc_ble_channel"
        private const val MAIN_CHANNEL_NAME = "CscService"

        // 700x23c circumference in meter
        private val circumference = BigDecimal("2.095")

        // m/s to km/h ratio
        private val msToKmSRatio = BigDecimal("3.6")
    }
}