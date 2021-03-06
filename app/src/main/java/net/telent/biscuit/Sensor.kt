package net.telent.biscuit

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikeCadencePcc
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikeSpeedDistancePcc
import com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc
import com.dsi.ant.plugins.antplus.pcc.AntPlusStrideSdmPcc
import com.dsi.ant.plugins.antplus.pcc.defines.DeviceState
import com.dsi.ant.plugins.antplus.pcc.defines.EventFlag
import com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal
import java.time.Instant
import java.util.*
import java.util.concurrent.Semaphore

interface ISensor {
    enum class SensorState { ABSENT, SEARCHING, PRESENT, BROKEN, FORBIDDEN }
    var state: SensorState
    var timestamp : Instant

    fun stateReport(): SensorSummary
    fun startSearch(context: Context, deviceNumber: Int = 0 )
    fun close()
}

open class Sensor(val name: String, val onStateChange: (s:Sensor) -> Unit = {}) {
    var state: ISensor.SensorState = ISensor.SensorState.ABSENT
        set(value) {
            Log.d("sensor", "sensor $name changed from $field to $value")
            field = value
            onStateChange(this)
        }

    var sensorName: String = ""
    var antDeviceNumber: Int? = null

    var timestamp : Instant = Instant.EPOCH

    fun stateReport(): SensorSummary {
        return SensorSummary(name, state, sensorName)
    }
}

open class AntSensor (name: String, onStateChange: (s:Sensor) -> Unit = {}) : Sensor(name, onStateChange) {
    protected var releaseHandle: PccReleaseHandle<*>? = null

    fun close() {
        this.releaseHandle?.close()
    }

    fun stateFromAnt(deviceState: DeviceState): ISensor.SensorState {
        return when (deviceState) {
            DeviceState.DEAD -> ISensor.SensorState.ABSENT
            DeviceState.CLOSED -> ISensor.SensorState.ABSENT
            DeviceState.TRACKING -> ISensor.SensorState.PRESENT
            DeviceState.SEARCHING -> ISensor.SensorState.SEARCHING
            DeviceState.PROCESSING_REQUEST -> ISensor.SensorState.SEARCHING
            DeviceState.UNRECOGNIZED -> ISensor.SensorState.BROKEN
        }
    }

    val stateChangeReceiver: AntPluginPcc.IDeviceStateChangeReceiver = AntPluginPcc.IDeviceStateChangeReceiver { antState ->
        state = stateFromAnt(antState)
    }
}

@Parcelize
data class SensorSummary(
        val name: String,
        val state : ISensor.SensorState,
        val sensorName : String
) : Parcelable

class SpeedSensor(onStateChange: (s:Sensor)-> Unit)  :ISensor , AntSensor("speed", onStateChange )  {
    var speed = 0.0
    var distance = 0.0
    var isCombinedSensor = false
    val wheelCircumference = 2.105 // 700x25, ref https://cateye.com/data/resources/Tire_size_chart_ENG.pdf

    override fun startSearch(context: Context, antDeviceNumber: Int  ) {
        this.close()
        this.releaseHandle = AntPlusBikeSpeedDistancePcc.requestAccess(context, antDeviceNumber, 0, antDeviceNumber > 0,
                    resultReceiver, stateChangeReceiver)

    }

    private val resultReceiver: AntPluginPcc.IPluginAccessResultReceiver<AntPlusBikeSpeedDistancePcc> = AntPluginPcc.IPluginAccessResultReceiver<AntPlusBikeSpeedDistancePcc> { pcc, resultCode, initialDeviceState ->
        if (resultCode == RequestAccessResult.SUCCESS) {
            sensorName = pcc!!.deviceName
            antDeviceNumber = pcc.antDeviceNumber
            isCombinedSensor = pcc.isSpeedAndCadenceCombinedSensor
            if(isCombinedSensor) Log.d("sensors", "combined speed")
            subscribeToEvents(pcc)
        }
        if (initialDeviceState != null)
            this@SpeedSensor.state = stateFromAnt(initialDeviceState)
    }
    private fun subscribeToEvents(pcc : AntPlusBikeSpeedDistancePcc) {
        pcc.subscribeCalculatedSpeedEvent(object : AntPlusBikeSpeedDistancePcc.CalculatedSpeedReceiver(BigDecimal(wheelCircumference)) {
            override fun onNewCalculatedSpeed(estTimestamp: Long,
                                              eventFlags: EnumSet<EventFlag>, calculatedSpeed: BigDecimal) {
                speed = calculatedSpeed.toDouble() * 3.6
                timestamp = Instant.now()
            }
        })
        pcc.subscribeRawSpeedAndDistanceDataEvent { estTimestamp, _eventFlags, timestampOfLastEvent, cumulativeRevolutions ->
            //estTimestamp - The estimated timestamp of when this event was triggered. Useful for correlating multiple events and determining when data was sent for more accurate data records.
            //eventFlags - Informational flags about the event.
            //timestampOfLastEvent - Sensor reported time counter value of last distance or speed computation (up to 1/200s accuracy). Units: s. Rollover: Every ~46 quadrillion s (~1.5 billion years).
            //cumulativeRevolutions - Total number of revolutions since the sensor was first connected. Note: If the subscriber is not the first PCC connected to the device the accumulation will probably already be at a value greater than 0 and the subscriber should save the first received value as a relative zero for itself. Units: revolutions. Rollover: Every ~9 quintillion revolutions.
            distance = cumulativeRevolutions.toDouble() * wheelCircumference
            timestamp = Instant.now()
        }
    }
}

class CadenceSensor(onStateChange: (s:Sensor)-> Unit) : ISensor, AntSensor("cadence", onStateChange ) {
    var cadence = 0.0
    var isCombinedSensor = false

    override fun startSearch(context: Context, antDeviceNumber: Int ) {
        this.close()
        this.releaseHandle =
            AntPlusBikeCadencePcc.requestAccess(context, antDeviceNumber, 0, antDeviceNumber > 0,
                    resultReceiver, stateChangeReceiver)
    }

    private val resultReceiver: AntPluginPcc.IPluginAccessResultReceiver<AntPlusBikeCadencePcc> = AntPluginPcc.IPluginAccessResultReceiver<AntPlusBikeCadencePcc> { pcc, resultCode, initialDeviceState ->
        if (resultCode == RequestAccessResult.SUCCESS) {
            sensorName = pcc!!.deviceName
            antDeviceNumber = pcc.antDeviceNumber
            isCombinedSensor = pcc.isSpeedAndCadenceCombinedSensor
            subscribeToEvents(pcc)
        }
        if (initialDeviceState != null)
            this@CadenceSensor.state = stateFromAnt(initialDeviceState)
    }
    private fun subscribeToEvents(pcc: AntPlusBikeCadencePcc) {
        pcc.subscribeCalculatedCadenceEvent { estTimestamp, eventFlags, calculatedCadence -> //Log.v(TAG, "Cadence:" + calculatedCadence.intValue());
            cadence = calculatedCadence.toDouble()
            timestamp = Instant.now()
        }
    }
}

class HeartSensor(onStateChange: (s:Sensor)-> Unit) : ISensor, AntSensor("heart", onStateChange ) {
    var hr : Int = 0
    override fun startSearch(context: Context, antDeviceNumber: Int ) {
        this.close()
        this.releaseHandle =  AntPlusHeartRatePcc.requestAccess(context, antDeviceNumber, 0,
                    resultReceiver, stateChangeReceiver)

    }

    private val resultReceiver : AntPluginPcc.IPluginAccessResultReceiver<AntPlusHeartRatePcc> = AntPluginPcc.IPluginAccessResultReceiver<AntPlusHeartRatePcc> { result, resultCode, initialDeviceState ->
        if (initialDeviceState != null)
            this@HeartSensor.state = stateFromAnt(initialDeviceState)
        if (resultCode == RequestAccessResult.SUCCESS) {
            subscribeToEvents(result!!)
        }
    }

    private fun subscribeToEvents(pcc: AntPlusHeartRatePcc) {
        sensorName = pcc.deviceName
        antDeviceNumber = pcc.antDeviceNumber
        pcc.subscribeHeartRateDataEvent { estTimestamp, eventFlags, computedHeartRate, heartBeatCount, heartBeatEventTime, dataState ->
            hr = computedHeartRate
            timestamp = Instant.now()
        }
    }
}

class StrideSensor(onStateChange: (s:Sensor)-> Unit) : ISensor, AntSensor("stride", onStateChange ) {
    var stridePerMinute = 0L
    var distance = 0.0
    var speed = 0.0

    override fun startSearch(context: Context, antDeviceNumber: Int) {
        this.close()
        this.releaseHandle =  AntPlusStrideSdmPcc.requestAccess(context, antDeviceNumber, 0,
                resultReceiver, stateChangeReceiver)
    }

    private val resultReceiver : AntPluginPcc.IPluginAccessResultReceiver<AntPlusStrideSdmPcc> = AntPluginPcc.IPluginAccessResultReceiver<AntPlusStrideSdmPcc> { result, resultCode, initialDeviceState ->
        if (initialDeviceState != null)
            this@StrideSensor.state = stateFromAnt(initialDeviceState)
        if (resultCode == RequestAccessResult.SUCCESS) {
            subscribeToEvents(result!!)
        }
    }

    private fun subscribeToEvents(pcc: AntPlusStrideSdmPcc) {
        sensorName = pcc.deviceName
        antDeviceNumber = pcc.antDeviceNumber
        // https://www.thisisant.com/developer/ant-plus/device-profiles#528_tab
        pcc.subscribeStrideCountEvent(object : AntPlusStrideSdmPcc.IStrideCountReceiver {
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
                        stridePerMinute = strideCount
                        lock.release()
                    } catch (e: InterruptedException) {
                        Log.e("sensors", "Unable to acquire lock to update running cadence", e)
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
        pcc.subscribeDistanceEvent { estTimestamp, eventFlags, distance ->
            this.distance = distance.toDouble()
            timestamp = Instant.now()
        }
        pcc.subscribeInstantaneousSpeedEvent { estTimestamp, eventFlags, instantaneousSpeed ->
            this.speed = instantaneousSpeed.toDouble()
            timestamp = Instant.now()
        }
    }
}

class PositionSensor(onStateChange: (s: Sensor) -> Unit) : ISensor , Sensor("location", onStateChange) {
    var latitude: Double? = null
    var longitude: Double? = null

    private lateinit var locationManager: LocationManager

    @SuppressLint("MissingPermission") // only called from fns that check the permission
    override fun startSearch(context: Context, antDeviceNumber: Int) {
        if(state == ISensor.SensorState.FORBIDDEN) return

        state = ISensor.SensorState.SEARCHING
        locationManager = context.getSystemService(Service.LOCATION_SERVICE) as LocationManager
        val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        latitude = location?.latitude
        longitude = location?.longitude
        timestamp = Instant.now()
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1.0f, locListener)
    }

    private val locListener = object : LocationListener {
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {

            Log.d(this.javaClass.name, "status changed $provider $status,$extras")
        }

        override fun onProviderDisabled(provider: String) {
            Log.d(this.javaClass.name, "location provider $provider disabled")
        }

        override fun onProviderEnabled(provider: String) {
            Log.d(this.javaClass.name, "location provider $provider enabled")
        }

        override fun onLocationChanged(loc: Location) {
            state = ISensor.SensorState.PRESENT
            sensorName = loc.provider
            latitude = loc.latitude
            longitude = loc.longitude
            timestamp = Instant.ofEpochMilli(loc.time)
            Log.d(this.javaClass.name, "locationChanged $loc")
        }
    }

    override fun close() {
        locationManager.removeUpdates(locListener)
    }
}
