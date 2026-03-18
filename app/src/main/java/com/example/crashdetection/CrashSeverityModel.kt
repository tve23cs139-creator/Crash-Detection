package com.example.crashdetection

import android.content.Context
import org.tensorflow.lite.InterpreterApi
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class CrashSeverityModel {

    enum class Severity {
        MINOR,
        SEVERE,
        EXTREME
    }

    private var interpreter: InterpreterApi? = null

    private val timeSteps = 50
    private val featuresPerStep = 6 // ax, ay, az, gx, gy, gz
    private val outputClasses = 3

    fun loadModel(context: Context, modelAssetName: String = "model.tflite") {
        if (interpreter != null) return
        val modelBuffer = loadModelFile(context, modelAssetName)
        interpreter = InterpreterApi.create(
            modelBuffer,
            InterpreterApi.Options().setNumThreads(2)
        )
    }

    fun preprocessSensorWindow(samples: List<SensorFusionCrashDetector.SensorSample>): FloatArray {
        val output = FloatArray(timeSteps * featuresPerStep)
        if (samples.isEmpty()) return output

        // Lightweight uniform sampling to a fixed-size model input window.
        val lastIndex = samples.lastIndex
        for (i in 0 until timeSteps) {
            val srcIndex = if (timeSteps == 1) {
                lastIndex
            } else {
                ((i.toFloat() / (timeSteps - 1)) * lastIndex).toInt()
            }
            val s = samples[srcIndex]
            val base = i * featuresPerStep
            output[base] = s.ax / 50f
            output[base + 1] = s.ay / 50f
            output[base + 2] = s.az / 50f
            output[base + 3] = s.gx / 20f
            output[base + 4] = s.gy / 20f
            output[base + 5] = s.gz / 20f
        }
        return output
    }

    fun runInference(inputFeatures: FloatArray): Severity {
        val localInterpreter = interpreter ?: return Severity.MINOR

        val inputBuffer = ByteBuffer
            .allocateDirect(4 * timeSteps * featuresPerStep)
            .order(ByteOrder.nativeOrder())

        inputFeatures.forEach { inputBuffer.putFloat(it) }
        inputBuffer.rewind()

        val output = Array(1) { FloatArray(outputClasses) }
        localInterpreter.run(inputBuffer, output)

        val probs = output[0]
        val maxIndex = probs.indices.maxByOrNull { probs[it] } ?: 0
        return when (maxIndex) {
            2 -> Severity.EXTREME
            1 -> Severity.SEVERE
            else -> Severity.MINOR
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    private fun loadModelFile(context: Context, modelAssetName: String): ByteBuffer {
        val fd = context.assets.openFd(modelAssetName)
        FileInputStream(fd.fileDescriptor).channel.use { fileChannel ->
            return fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                fd.startOffset,
                fd.declaredLength
            )
        }
    }
}
