package com.example.crashdetection

import android.content.Context
import android.util.Log
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
    private var modelHealthy = false

    private val timeSteps = 50
    private val featuresPerStep = 6 // ax, ay, az, gx, gy, gz
    private val outputClasses = 3
    val labels = listOf("MINOR", "SEVERE", "EXTREME")

    fun loadModel(context: Context, modelAssetName: String = "model.tflite") {
        if (interpreter != null) return
        val modelBuffer = loadModelFile(context, modelAssetName)
        interpreter = InterpreterApi.create(
            modelBuffer,
            InterpreterApi.Options().setNumThreads(2)
        )
        verifyModelHealth()
    }

    fun verifyModelHealth(): Boolean {
        val localInterpreter = interpreter
        if (localInterpreter == null) {
            modelHealthy = false
            Log.e(TAG, "ML Model Health Check failed: interpreter is not initialized")
            return false
        }

        return try {
            val inputBuffer = ByteBuffer
                .allocateDirect(4 * timeSteps * featuresPerStep)
                .order(ByteOrder.nativeOrder())
            repeat(timeSteps * featuresPerStep) { inputBuffer.putFloat(0f) }
            inputBuffer.rewind()

            val output = Array(1) { FloatArray(outputClasses) }
            localInterpreter.run(inputBuffer, output)

            if (output[0].size != labels.size) {
                modelHealthy = false
                Log.e(TAG, "ML Model Health Check failed: output classes and labels mismatch")
                return false
            }

            val testIndex = output[0].indices.maxByOrNull { output[0][it] } ?: 0
            if (testIndex !in labels.indices) {
                modelHealthy = false
                Log.e(TAG, "ML Model Health Check failed: invalid output index $testIndex")
                return false
            }

            modelHealthy = true
            Log.i(TAG, "✅ ML Model Health Check: PASSED")
            true
        } catch (e: Exception) {
            modelHealthy = false
            Log.e(TAG, "ML Model Health Check failed: ${e.message}", e)
            false
        }
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
        val localInterpreter = interpreter
            ?: throw IllegalStateException("Model interpreter is unavailable")
        if (!modelHealthy) {
            throw IllegalStateException("Model health check failed")
        }

        val inputBuffer = ByteBuffer
            .allocateDirect(4 * timeSteps * featuresPerStep)
            .order(ByteOrder.nativeOrder())

        inputFeatures.forEach { inputBuffer.putFloat(it) }
        inputBuffer.rewind()

        val output = Array(1) { FloatArray(outputClasses) }
        localInterpreter.run(inputBuffer, output)

        return classify(output[0])
    }

    fun classify(modelOutput: FloatArray): Severity {
        if (modelOutput.isEmpty()) {
            throw IllegalArgumentException("Model output is empty")
        }
        val maxIndex = modelOutput.indices.maxByOrNull { modelOutput[it] } ?: 0
        if (maxIndex !in labels.indices) {
            throw IllegalStateException("Invalid model output index: $maxIndex")
        }
        return when (labels[maxIndex]) {
            "EXTREME" -> Severity.EXTREME
            "SEVERE" -> Severity.SEVERE
            else -> Severity.MINOR
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        modelHealthy = false
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

    companion object {
        private const val TAG = "CrashSeverityModel"
    }
}
