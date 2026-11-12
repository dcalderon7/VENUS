package com.example.venus.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class VenusSensorManager(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var onFlipDownAction: (() -> Unit)? = null
    private var isFaceDown = false

    fun isFlipToPauseEnabled(): Boolean {
        val prefs = context.getSharedPreferences("venus_sensor_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("flip_to_pause", true)
    }

    fun setFlipToPauseEnabled(enabled: Boolean) {
        val prefs = context.getSharedPreferences("venus_sensor_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("flip_to_pause", enabled).apply()
    }

    fun startListening(onFlipDown: () -> Unit) {
        this.onFlipDownAction = onFlipDown
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
        onFlipDownAction = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isFlipToPauseEnabled()) return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val z = event.values[2]
            if (z < -7.0f && !isFaceDown) {
                isFaceDown = true
                onFlipDownAction?.invoke()
            } else if (z > -2.0f) {
                isFaceDown = false
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
