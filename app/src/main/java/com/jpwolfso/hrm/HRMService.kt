package com.jpwolfso.hrm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.widget.Toast
import androidx.preference.PreferenceManager

lateinit var sensorManager: SensorManager
var sensorId: Int? = null
var measureTime: Int? = null
lateinit var hrmsensor: Sensor
var uptimeNanos: Long? = null

var count: Int = 0
lateinit var movingAverageData: ArrayList<Double>
lateinit var movingAverageTime: ArrayList<Double>

lateinit var timeData: ArrayList<Double>
lateinit var rawData: ArrayList<Double>
lateinit var peakTimes: ArrayList<Double>
lateinit var peakDiffs: ArrayList<Double>

var isMeasuring = false
var timestampSeconds: Double? = null
var firstPoint = true

class HRMService : Service(), SensorEventListener {

    private val binder = MyBinder()

    inner class MyBinder : Binder() {
        fun getService(): HRMService = this@HRMService
    }

    override fun onBind(p0: Intent?): IBinder? {
        sensorId = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            .getString("hrm_type", "65572")!!.toInt()
        measureTime = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            .getInt("hrm_time", 10) + 2
        startMeasure(applicationContext, sensorId!!)
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stopMeasure()
        return super.onUnbind(intent)
    }

    fun startMeasure(context: Context, sensorId: Int) {

        uptimeNanos = SystemClock.elapsedRealtimeNanos()
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        var hasSensor = false
        for (sensor in sensorManager.getSensorList(Sensor.TYPE_ALL)) {
            if (sensor.type == sensorId) {
                hasSensor = true
                break
            } else {
                continue
            }
        }

        if (hasSensor) {
            hrmsensor = sensorManager.getDefaultSensor(sensorId)
            sensorManager.registerListener(this, hrmsensor, SensorManager.SENSOR_DELAY_NORMAL)

            isMeasuring = true

            movingAverageData = ArrayList()
            movingAverageTime = ArrayList()
        } else {
            stopMeasure()
            sendBroadcast(
                Intent().setAction("unsupportedSensor").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun stopMeasure() {
        if (isMeasuring) {
            sensorManager.unregisterListener(this, hrmsensor)
            firstPoint = true
            isMeasuring = false
        }
    }

    override fun onSensorChanged(p0: SensorEvent?) {
        timestampSeconds = (p0!!.timestamp - uptimeNanos!!).toDouble() / 1000000000

        if (timestampSeconds!! > 2 && timestampSeconds!! <= measureTime!!) { // Discard first two seconds of data, measure for 10 seconds
            if (count < 3) { // moving average for smoother results
                count += 1
                movingAverageData.add(p0.values[0].toDouble())
                movingAverageTime.add(timestampSeconds!! - 2)
            } else if (count == 3) {
                count = 0
                var x = movingAverageTime.sum() / movingAverageTime.count()
                var y = movingAverageData.sum() / movingAverageData.count()
                var xy: DoubleArray = arrayOf(x, y).toDoubleArray()

                movingAverageData = ArrayList()
                movingAverageTime = ArrayList()
                if (firstPoint) {

                    //  if (applicationContext is MainActivity) {
                    sendBroadcast(
                        Intent()
                            .setAction("sensorData")
                            .putExtra("sensorData", xy)
                            .putExtra("firstPoint", true)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    // }

                    rawData = ArrayList()
                    timeData = ArrayList()
                    peakTimes = ArrayList()
                    peakDiffs = ArrayList()
                    firstPoint = false
                } else {

                    // if (applicationContext is MainActivity) {
                    sendBroadcast(
                        Intent()
                            .setAction("sensorData")
                            .putExtra("sensorData", xy)
                            .putExtra("firstPoint", false)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    // }

                    rawData.add(y)
                    timeData.add(x)
                }

            }
        } else if (timestampSeconds!! > measureTime!!) {
            stopMeasure()

            for (i in 3 until rawData.size - 3) {
                rawData.apply {
                    if (get(i) > get(i - 1) && get(i + 1) < get(i)
                        && get(i) > get(i - 2) && get(i + 2) < get(i)
                        && get(i) > get(i - 3) && get(i + 3) < get(i)
                    ) {
                        peakTimes.add(timeData[i])
                    }
                }
            }

            for (i in 1 until peakTimes.size) {
                var timeDiff = peakTimes[i] - peakTimes[i - 1]
                if (timeDiff > 0.5 && timeDiff < 2.0) { // 1 beat should be between 0.5s (120bpm) and 2s (30bpm)
                    peakDiffs.add(timeDiff)
                }
            }

            var spb = peakDiffs.sum() / peakDiffs.size // average seconds per beat
            var bpm = 60 / spb // average beats per minute

            var vibrator: Vibrator =
                applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    100,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )

            if (bpm.toInt() != 0) {
                //if (applicationContext is MainActivity) {
                sendBroadcast(
                    Intent().setAction("finishMeasureUI").putExtra("bpm", bpm)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                sendBroadcast(
                    Intent().setAction("finishMeasureTile").putExtra("bpm", bpm)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
//                } else if (applicationContext is HRMTileService) {
//                    Toast.makeText(
//                        applicationContext,
//                        bpm.toInt().toString() + " BPM",
//                        Toast.LENGTH_LONG
//                    ).show()
//                }
            } else {
                Toast.makeText(
                    applicationContext,
                    "Error measuring heart rate",
                    Toast.LENGTH_LONG
                ).show()
            }

        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        if (p1 == SensorManager.SENSOR_STATUS_ACCURACY_LOW || p1 == SensorManager.SENSOR_STATUS_UNRELIABLE || p1 == SensorManager.SENSOR_STATUS_NO_CONTACT) {
            Toast.makeText(
                applicationContext,
                "Unable to read heart rate with sufficient accuracy",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

}