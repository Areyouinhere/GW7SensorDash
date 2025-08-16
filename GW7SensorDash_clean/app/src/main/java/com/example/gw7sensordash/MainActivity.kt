package com.example.gw7sensordash

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager

    private val readings = mutableStateListOf<RowData>()
    private fun updateReading(name: String, value: String) {
        val idx = readings.indexOfFirst { it.name == name }
        if (idx >= 0) readings[idx] = readings[idx].copy(value = value)
        else readings.add(RowData(name, value))
    }

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        listOf(
            "Accelerometer", "Gyroscope", "Magnetometer",
            "Barometer", "Ambient Light", "Step Counter", "Heart Rate"
        ).forEach { updateReading(it, "—") }

        val toRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED) {
            toRequest += Manifest.permission.BODY_SENSORS
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED) {
            toRequest += Manifest.permission.ACTIVITY_RECOGNITION
        }
        if (toRequest.isNotEmpty()) requestPermissions.launch(toRequest.toTypedArray())

        setContent {
            MaterialTheme {
                Scaffold(timeText = { TimeText() }) {
                    SensorList(readings)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        register(Sensor.TYPE_ACCELEROMETER) { values ->
            updateReading("Accelerometer", fmt3(values))
        }
        register(Sensor.TYPE_GYROSCOPE) { values ->
            updateReading("Gyroscope", fmt3(values))
        }
        register(Sensor.TYPE_MAGNETIC_FIELD) { values ->
            updateReading("Magnetometer", fmt3(values))
        }
        register(Sensor.TYPE_PRESSURE) { values ->
            updateReading("Barometer", "${values[0].toInt()} hPa")
        }
        register(Sensor.TYPE_LIGHT) { values ->
            updateReading("Ambient Light", "${values[0].toInt()} lx")
        }
        register(Sensor.TYPE_STEP_COUNTER) { values ->
            updateReading("Step Counter", values[0].toInt().toString())
        }
        register(Sensor.TYPE_HEART_RATE) { values ->
            val bpm = values[0]
            if (!bpm.isNaN()) updateReading("Heart Rate", "${bpm.toInt()} bpm")
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    private fun register(type: Int, onValues: (FloatArray) -> Unit) {
        val sensor = sensorManager.getDefaultSensor(type)
        if (sensor != null) {
            callbacks[type] = onValues
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            val name = when (type) {
                Sensor.TYPE_ACCELEROMETER -> "Accelerometer"
                Sensor.TYPE_GYROSCOPE -> "Gyroscope"
                Sensor.TYPE_MAGNETIC_FIELD -> "Magnetometer"
                Sensor.TYPE_PRESSURE -> "Barometer"
                Sensor.TYPE_LIGHT -> "Ambient Light"
                Sensor.TYPE_STEP_COUNTER -> "Step Counter"
                Sensor.TYPE_HEART_RATE -> "Heart Rate"
                else -> "Sensor $type"
            }
            updateReading(name, "—")
        }
    }

    private val callbacks = mutableMapOf<Int, (FloatArray) -> Unit>()

    override fun onSensorChanged(event: SensorEvent) {
        callbacks[event.sensor.type]?.invoke(event.values)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }
}

private fun fmt3(values: FloatArray): String =
    "x=${values.getOrNull(0)?.let { round2(it) } ?: "—"}, " +
    "y=${values.getOrNull(1)?.let { round2(it) } ?: "—"}, " +
    "z=${values.getOrNull(2)?.let { round2(it) } ?: "—"}"

private fun round2(f: Float): String = String.format("%.2f", f)

data class RowData(val name: String, val value: String)

@Composable
fun SensorList(items: List<RowData>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items) { row -> SensorRow(row) }
    }
}

@Composable
fun SensorRow(row: RowData) {
    Column { Text(row.name); Text(row.value) }
}
