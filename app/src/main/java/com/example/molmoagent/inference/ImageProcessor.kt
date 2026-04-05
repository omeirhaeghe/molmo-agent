package com.example.molmoagent.inference

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageProcessor @Inject constructor() {

    companion object {
        // Cap each dimension at 1280px so portrait screens aren't squashed.
        // A 1080×2400 phone resizes to 576×1280 (vs the old 324×720), giving
        // the model much more detail to work with.
        const val TARGET_WIDTH = 1280
        const val TARGET_HEIGHT = 1280
        const val JPEG_QUALITY = 85
    }

    /**
     * Resize a bitmap to fit within the target dimensions while preserving aspect ratio.
     */
    fun resize(bitmap: Bitmap): Bitmap {
        val srcWidth = bitmap.width
        val srcHeight = bitmap.height

        val widthRatio = TARGET_WIDTH.toFloat() / srcWidth
        val heightRatio = TARGET_HEIGHT.toFloat() / srcHeight
        val scale = minOf(widthRatio, heightRatio)

        val newWidth = (srcWidth * scale).toInt()
        val newHeight = (srcHeight * scale).toInt()

        // Convert from hardware bitmap if necessary
        val softBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }

        return Bitmap.createScaledBitmap(softBitmap, newWidth, newHeight, true)
    }

    /**
     * Encode a bitmap as a base64 JPEG string.
     */
    fun toBase64(bitmap: Bitmap): String {
        val resized = resize(bitmap)
        val stream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        val bytes = stream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Get the actual dimensions of a processed image (after resize).
     * Used to map normalized coordinates back to screen pixels.
     */
    fun getProcessedDimensions(originalBitmap: Bitmap): Pair<Int, Int> {
        val resized = resize(originalBitmap)
        return resized.width to resized.height
    }
}
