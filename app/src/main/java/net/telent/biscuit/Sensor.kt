package net.telent.biscuit

import android.content.Context
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
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.Semaphore

open class Sensor(val name: String, val onStateChange: (s:Sensor) -> Unit = {}) {
    enum class SensorState { ABSENT, SEARCHING, PRESENT, BROKEN }

    var state: SensorState = SensorState.ABSENT
        set(value) {
            Log.d("sensor", "sensor $name changed from $field to $value")
            field = value
        }

    var sensorName: String = ""
    protected var releaseHandle: PccReleaseHandle<*>? = null
    open fun startSearch(context: Context) {
        this.releaseHandle?.close()
        Log.d("sensor", "started search for $name")
    }

    fun close() {
        this.releaseHandle?.close()
    }

    fun stateFromAnt(deviceState: DeviceState): SensorState {
        return when (deviceState) {
            DeviceState.DEAD -> SensorState.BROKEN
            DeviceState.CLOSED -> SensorState.ABSENT
            DeviceState.TRACKING -> SensorState.PRESENT
            DeviceState.SEARCHING -> SensorState.SEARCHING
            DeviceState.PROCESSING_REQUEST -> SensorState.SEARCHING
            DeviceState.UNRECOGNIZED -> SensorState.BROKEN
        }
    }

    val stateChangeReceiver: AntPluginPcc.IDeviceStateChangeReceiver = AntPluginPcc.IDeviceStateChangeReceiver { state ->
        this@Sensor.state = stateFromAnt(state)
        onStateChange(this)
    }

    fun stateReport(): Triple<String, SensorState, String> {
        return Triple(name, state, sensorName)
    }
}

class SpeedSensor(onStateChange: (s:Sensor)-> Unit) : Sensor("speed", onStateChange ) {
    // what if we put the speed etc properties in here instead of in BiscuitService?
    var speed = 0.0
    var distance = 0.0
    var combinedSensor = false

    override fun startSearch(context: Context) {
        super.startSearch(context)
        this.releaseHandle = AntPlusBikeSpeedDistancePcc.requestAccess(context, 0, 0, false,
                    resultReceiver, stateChangeReceiver)

    }
    private val resultReceiver: AntPluginPcc.IPluginAccessResultReceiver<AntPlusBikeSpeedDistancePcc> = AntPluginPcc.IPluginAccessResultReceiver<AntPlusBikeSpeedDistancePcc> { result, resultCode, initialDeviceState ->
        if (initialDeviceState != null)
            this@SpeedSensor.state = stateFromAnt(initialDeviceState)
        if (resultCode == RequestAccessResult.SUCCESS) {
            subscribeToEvents(result!!)
        }
    }
    private fun subscribeToEvents(pcc : AntPlusBikeSpeedDistancePcc) {
        sensorName = pcc.deviceName
        pcc.subscribeCalculatedSpeedEvent(object : AntPlusBikeSpeedDistancePcc.CalculatedSpeedReceiver(BigDecimal("2.205")) {
            override fun onNewCalculatedSpeed(estTimestamp: Long,
                                              eventFlags: EnumSet<EventFlag>, calculatedSpeed: BigDecimal) {
                speed = calculatedSpeed.toDouble() * 3.6
            }
        })
        pcc.subscribeRawSpeedAndDistanceDataEvent { estTimestamp, _eventFlags, timestampOfLastEvent, cumulativeRevolutions ->
            //estTimestamp - The estimated timestamp of when this event was triggered. Useful for correlating multiple events and determining when data was sent for more accurate data records.
            //eventFlags - Informational flags about the event.
            //timestampOfLastEvent - Sensor reported time counter value of last distance or speed computation (up to 1/200s accuracy). Units: s. Rollover: Every ~46 quadrillion s (~1.5 billion years).
            //cumulativeRevolutions - Total number of revolutions since the sensor was first connected. Note: If the subscriber is not the first PCC connected to the device the accumulation will probably already be at a value greater than 0 and the subscriber should save the first received value as a relative zero for itself. Units: revolutions. Rollover: Every ~9 quintillion revolutions.
            distance = cumulativeRevolutions.toDouble() * 2.205
        }
        combinedSensor = pcc.isSpeedAndCadenceCombinedSensor
//            if (pcc.isSpeedAndCadenceCombinedSensor && !combinedSensorConnected) {
//                // if this is  a combined sensor, subscribe to its cadence events
//                combinedSensorConnected = true
//                sensors.cadence.startSearchBy(this@BiscuitService) {
//                    com.dsi.ant.plugins.antplus.pcc.AntPlusBikeCadencePcc.requestAccess(applicationContext, pcc.antDeviceNumber, 0, true,
//                            mBCResultReceiver, mBCDeviceStateChangeReceiver)
//                }
//            }
    }
}

class CadenceSensor(onStateChange: (s:Sensor)-> Unit) : Sensor("cadence", onStateChange ) {
    var cadence = 0.0
    var combinedSensor = false

    override fun startSearch(context: Context) {
        super.startSearch(context)
        this.releaseHandle =
            AntPlusBikeCadencePcc.requestAccess(context, 0, 0, false,
                    resultReceiver, stateChangeReceiver)
    }

    private val resultReceiver: AntPluginPcc.IPluginAccessResultReceiver<AntPlusBikeCadencePcc> = AntPluginPcc.IPluginAccessResultReceiver<AntPlusBikeCadencePcc> { result, resultCode, initialDeviceState ->
        if (initialDeviceState != null)
            this@CadenceSensor.state = stateFromAnt(initialDeviceState)
        if (resultCode == RequestAccessResult.SUCCESS) {
            subscribeToEvents(result!!)
        }
    }
    private fun subscribeToEvents(pcc: AntPlusBikeCadencePcc) {
        pcc.subscribeCalculatedCadenceEvent { estTimestamp, eventFlags, calculatedCadence -> //Log.v(TAG, "Cadence:" + calculatedCadence.intValue());
            cadence = calculatedCadence.toDouble()
        }
        sensorName = pcc.deviceName
        combinedSensor = pcc.isSpeedAndCadenceCombinedSensor
//        pcc.subscribeRawCadenceDataEvent { estTimestamp, eventFlags, timestampOfLastEvent, cumulativeRevolutions ->
//            cumulativeCrankRevolution = cumulativeRevolutions
//            lastCrankEventTime = (timestampOfLastEvent.toDouble() * 1024.0).toInt()
//            lastCadenceTimestamp = estTimestamp
//            lastUpdateTime = Instant.ofEpochMilli(estTimestamp)
//        }
//            if (pcc.isSpeedAndCadenceCombinedSensor && !combinedSensorConnected) {
//                // reconnect speed sensor as a combined sensor
//                combinedSensorConnected = true
//                sensors.speed.startSearch(this@BiscuitService)
//            }

    }
}

class HeartSensor(onStateChange: (s:Sensor)-> Unit) : Sensor("heart", onStateChange ) {
    var hr : Int = 0
    override fun startSearch(context: Context) {
        super.startSearch(context)
        this.releaseHandle =  AntPlusHeartRatePcc.requestAccess(context, 0, 0,
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
        pcc.subscribeHeartRateDataEvent { estTimestamp, eventFlags, computedHeartRate, heartBeatCount, heartBeatEventTime, dataState ->
            hr = computedHeartRate
        }
    }
}

class StrideSensor(onStateChange: (s:Sensor)-> Unit) : Sensor("stride", onStateChange ) {
    var stridePerMinute = 0L
    var distance = 0.0
    var speed = 0.0

    override fun startSearch(context: Context) {
        super.startSearch(context)
        this.releaseHandle =  AntPlusStrideSdmPcc.requestAccess(context, 0, 0,
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
        }
        pcc.subscribeInstantaneousSpeedEvent { estTimestamp, eventFlags, instantaneousSpeed ->
            this.speed = instantaneousSpeed.toDouble()
        }
    }
}
