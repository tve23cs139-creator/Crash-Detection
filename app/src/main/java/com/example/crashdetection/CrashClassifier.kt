package com.example.crashdetection

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class CrashClassifier(
    context: Context,
    modelAssetName: String = "model.tflite",
    numThreads: Int = 2
) : Closeable {

    private val interpreter: Interpreter

    init {
        val modelBuffer = loadModelFile(context, modelAssetName)
        interpreter = Interpreter(
            modelBuffer,
            Interpreter.Options().setNumThreads(numThreads)
        )
    }

    fun predictClass(window: Array<FloatArray>): Int {
        require(window.size == TIME_STEPS) {
            "Expected $TIME_STEPS timesteps, got ${window.size}"
        }
        require(window.all { it.size == FEATURES }) {
            "Each timestep must have $FEATURES features [ax, ay, az, gx, gy, gz]"
        }

        val inputBuffer = ByteBuffer
            .allocateDirect(4 * TIME_STEPS * FEATURES)
            .order(ByteOrder.nativeOrder())

        for (t in 0 until TIME_STEPS) {
            for (f in 0 until FEATURES) {
                inputBuffer.putFloat(window[t][f])
            }
        }
        inputBuffer.rewind()

        val output = Array(1) { FloatArray(OUTPUT_CLASSES) }
        interpreter.run(inputBuffer, output)

        return output[0].indices.maxByOrNull { output[0][it] } ?: 0
    }

    override fun close() {
        interpreter.close()
    }

    private fun loadModelFile(context: Context, modelAssetName: String): ByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelAssetName)
        FileInputStream(assetFileDescriptor.fileDescriptor).channel.use { channel ->
            return channel.map(
                FileChannel.MapMode.READ_ONLY,
                assetFileDescriptor.startOffset,
                assetFileDescriptor.declaredLength
            )
        }
    }

    companion object {
        const val TIME_STEPS = 50
        const val FEATURES = 6
        const val OUTPUT_CLASSES = 4
    }
}
