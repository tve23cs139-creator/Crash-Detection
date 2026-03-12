package com.example.crashdetection

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class SeverityClassifier(private val context: Context) {
    private var interpreter: Interpreter? = null

    init {
        runCatching {
            context.assets.openFd(MODEL_FILE).use { afd ->
                afd.createInputStream().channel.use { channel ->
                    val mapped = channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        afd.startOffset,
                        afd.declaredLength
                    )
                    interpreter = Interpreter(mapped)
                }
            }
        }.onFailure {
            Log.w(TAG, "Unable to load TFLite model. Falling back to rule-based scoring.", it)
        }
    }

    fun classify(segment: CrashSegment): String {
        val mlPrediction = runCatching { predictWithModel(segment) }.getOrNull()
        return mlPrediction ?: classifyFallback(segment)
    }

    private fun predictWithModel(segment: CrashSegment): String? {
        val activeInterpreter = interpreter ?: return null
        val samples = segment.samples.takeLast(WINDOW_SIZE)
        val padded = if (samples.size < WINDOW_SIZE) {
            List(WINDOW_SIZE - samples.size) { SensorSample(0L, 0f, 0f) } + samples
        } else {
            samples
        }

        val input = ByteBuffer.allocateDirect(WINDOW_SIZE * FEATURE_SIZE * 4)
            .order(ByteOrder.nativeOrder())

        padded.forEach { sample ->
            input.putFloat(sample.gForce)
            input.putFloat(sample.gyroMagnitude)
        }
        input.rewind()

        val output = Array(1) { FloatArray(3) }
        activeInterpreter.run(input, output)

        val probabilities = output[0]
        val index = probabilities.indices.maxByOrNull { probabilities[it] } ?: return null

        return when (index) {
            0 -> "MINOR"
            1 -> "MODERATE"
            else -> "SEVERE"
        }
    }

    private fun classifyFallback(segment: CrashSegment): String {
        return when {
            segment.peakGForce >= 16f || segment.orientationShift >= 6f || segment.peakGyro >= 12f -> "SEVERE"
            segment.peakGForce >= 9f || segment.orientationShift >= 3f || segment.peakGyro >= 6f -> "MODERATE"
            else -> "MINOR"
        }
    }

    companion object {
        private const val TAG = "SeverityClassifier"
        private const val MODEL_FILE = "crash_severity.tflite"
        private const val WINDOW_SIZE = 30
        private const val FEATURE_SIZE = 2
    }
}
