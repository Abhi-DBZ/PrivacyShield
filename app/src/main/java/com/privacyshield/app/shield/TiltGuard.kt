package com.privacyshield.app.shield

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Remembers which way "down" points when the owner holds the phone (the gravity vector in the
 * phone's own axes) and reports how many degrees the phone has turned away from that since.
 * Turning the screen towards a neighbour, flipping it to someone opposite or laying it flat on a
 * table all show up as a large angle.
 */
class TiltGuard(context: Context, private val onAngle: (Float?) -> Unit) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gravitySensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val sensor = gravitySensor ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    /** Raw accelerometer also sees hand shake, so smooth it harder. */
    private val smoothing = if (gravitySensor != null) 0.35f else 0.12f

    private val g = FloatArray(3)
    private var hasG = false
    private var base: FloatArray? = null
    private var recenterAt = 0L
    private var running = false

    val available get() = sensor != null

    fun start() {
        if (running || sensor == null) return
        running = true
        hasG = false
        // A little longer than a manual recenter: the phone may still be turning (e.g. out of landscape).
        recenter(delayMs = 600)
        sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        if (!running) return
        running = false
        sm.unregisterListener(this)
        onAngle(null)
    }

    /** "This is how I hold it": take the position a moment from now as the owner's. */
    fun recenter(delayMs: Long = 350) {
        base = null
        recenterAt = SystemClock.elapsedRealtime() + delayMs
        onAngle(null)
    }

    override fun onSensorChanged(e: SensorEvent) {
        val x = e.values[0]; val y = e.values[1]; val z = e.values[2]
        val n = sqrt(x * x + y * y + z * z)
        if (n < 1e-3f) return
        if (!hasG) {
            g[0] = x / n; g[1] = y / n; g[2] = z / n; hasG = true
        } else {
            g[0] += smoothing * (x / n - g[0]); g[1] += smoothing * (y / n - g[1]); g[2] += smoothing * (z / n - g[2])
        }
        val b = base
        if (b == null) {
            if (SystemClock.elapsedRealtime() >= recenterAt) base = unit(g)
            return
        }
        val u = unit(g)
        val dot = (u[0] * b[0] + u[1] * b[1] + u[2] * b[2]).coerceIn(-1f, 1f)
        onAngle(Math.toDegrees(acos(dot).toDouble()).toFloat())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun unit(v: FloatArray): FloatArray {
        val n = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).coerceAtLeast(1e-6f)
        return floatArrayOf(v[0] / n, v[1] / n, v[2] / n)
    }
}
