package net.telent.biscuit

import android.content.Context
import android.util.Log
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikeCadencePcc
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikeSpeedDistancePcc
import com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc
import com.dsi.ant.plugins.antplus.pcc.defines.DeviceState
import com.dsi.ant.plugins.antplus.pcc.defines.EventFlag
import com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc
import com.dsi.ant.plugins.antplus.pccbase.AntPlusCommonPcc
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle
import java.math.BigDecimal
import java.util.*

open class Sensor(val name: String) {
    enum class SensorState { ABSENT, SEARCHING, PRESENT, BROKEN }
    var state : SensorState = SensorState.ABSENT
        set(value) {
            Log.d("sensor", "sensor $name changed from $field to $value")
            field = value
        }
    var pcc : AntPlusCommonPcc? = null
    private var releaseHandle : PccReleaseHandle<*>? = null
    fun startSearchBy(context: Context, f: () -> PccReleaseHandle<*>)
    {
        this.releaseHandle?.close()
        this.releaseHandle = f()
        Log.d("sensor", "started search for $name")
    }
    fun close(){
        this.releaseHandle?.close()
    }

    fun stateFromAnt(deviceState : DeviceState): SensorState {
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
    }
}

class SpeedSensor : Sensor("speed") {
    // what if we put the speed etc properties in here instead of in BiscuitService?
    var speed = 0.0
    var distance = 0.0
    fun startSearch(context: Context) {
        startSearchBy(context) {
            AntPlusBikeSpeedDistancePcc.requestAccess(context, 0, 0, false,
                    resultReceiver, stateChangeReceiver)
        }
    }
    private val resultReceiver: AntPluginPcc.IPluginAccessResultReceiver<AntPlusBikeSpeedDistancePcc> = AntPluginPcc.IPluginAccessResultReceiver<AntPlusBikeSpeedDistancePcc> { result, resultCode, initialDeviceState ->
        if (initialDeviceState != null)
            this@SpeedSensor.state = stateFromAnt(initialDeviceState)
        if (resultCode == RequestAccessResult.SUCCESS) {
            subscribeToEvents(result!!)
        }
    }
    private fun subscribeToEvents(pcc : AntPlusBikeSpeedDistancePcc) {
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

class CadenceSensor : Sensor("cadence") {
    var cadence = 0.0

    fun startSearch(context: Context) {
        startSearchBy(context) {
            AntPlusBikeCadencePcc.requestAccess(context, 0, 0, false,
                    resultReceiver, stateChangeReceiver)
        }
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

abstract class HeartSensor : Sensor("heart") {
    fun startSearch(context: Context) {
        startSearchBy(context) {
            AntPlusHeartRatePcc.requestAccess(context, 0, 0,
                    resultReceiver, stateChangeReceiver)
        }
    }
    abstract fun subscribeToEvents(pcc: AntPlusHeartRatePcc)

    private val resultReceiver : AntPluginPcc.IPluginAccessResultReceiver<AntPlusHeartRatePcc> = AntPluginPcc.IPluginAccessResultReceiver<AntPlusHeartRatePcc> { result, resultCode, initialDeviceState ->
        if (initialDeviceState != null)
            this@HeartSensor.state = stateFromAnt(initialDeviceState)
        if (resultCode == RequestAccessResult.SUCCESS) {
            subscribeToEvents(result!!)
        }
    }
}