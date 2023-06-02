package com.example.grocery

import org.tensorflow.lite.Interpreter
import java.nio.MappedByteBuffer
import android.graphics.Bitmap
import android.content.res.AssetManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.io.FileInputStream
import java.io.IOException

class Classifier(assetManager: AssetManager, modelPath: String) {

    private val tflite: Interpreter
    private val inputSize: Int = 100 // replace with your input size
    private val pixelSize: Int = 3 // replace with your pixel size (3 for RGB, 1 for grayscale)

    init {
        val tfliteOptions = Interpreter.Options()
        tfliteOptions.setNumThreads(2) // Set number of threads to improve performance.
        val model = loadModelFile(assetManager, modelPath)
        tflite = Interpreter(model, tfliteOptions)
    }

    fun classify(bitmap: Bitmap): Array<FloatArray> {
        val resizedBitmap = resizeBitmap(bitmap, inputSize, inputSize)
        val byteBuffer = convertBitmapToByteBuffer(resizedBitmap)
        val result = Array(1) { FloatArray(131) } // replace outputSize with your model's output size
        tflite.run(byteBuffer, result)
        return result
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * pixelSize)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(inputSize * inputSize)

        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var pixel = 0
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val value = intValues[pixel++]

                byteBuffer.putFloat(((value shr 16 and 0xFF) - imageMean) / imageStd)
                byteBuffer.putFloat(((value shr 8 and 0xFF) - imageMean) / imageStd)
                byteBuffer.putFloat(((value and 0xFF) - imageMean) / imageStd)
            }
        }
        return byteBuffer
    }

    private fun loadModelFile(assetManager: AssetManager, modelPath: String): ByteBuffer {
        val fileDescriptor = assetManager.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }



    companion object {
        const val imageMean = 127.5f
        const val imageStd  = 127.5f
    }
}
private fun resizeBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap {
    return Bitmap.createScaledBitmap(bitmap, width, height, false)
}
