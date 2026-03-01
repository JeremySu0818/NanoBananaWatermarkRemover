package com.jeremysu0818.nanobananawatermarkremover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color

class WatermarkRemover(private val context: Context) {

    private val ALPHA_THRESHOLD = 0.002f
    private val MAX_ALPHA = 0.99f
    private val LOGO_VALUE = 255f

    data class WatermarkConfig(
        val logoSize: Int,
        val marginRight: Int,
        val marginBottom: Int
    )

    private fun detectWatermarkConfig(imageWidth: Int, imageHeight: Int): WatermarkConfig {
        return if (imageWidth > 1024 && imageHeight > 1024) {
            WatermarkConfig(logoSize = 96, marginRight = 64, marginBottom = 64)
        } else {
            WatermarkConfig(logoSize = 48, marginRight = 32, marginBottom = 32)
        }
    }

    private fun calculateAlphaMap(bgBitmap: Bitmap): FloatArray {
        val width = bgBitmap.width
        val height = bgBitmap.height
        val alphaMap = FloatArray(width * height)
        val pixels = IntArray(width * height)
        
        bgBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val color = pixels[i]
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)

            val maxChannel = maxOf(r, maxOf(g, b))
            alphaMap[i] = maxChannel / 255.0f
        }

        return alphaMap
    }

    fun removeWatermark(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height

        val config = detectWatermarkConfig(width, height)
        val logoSize = config.logoSize
        val x = width - config.marginRight - logoSize
        val y = height - config.marginBottom - logoSize

        val bgResId = if (logoSize == 96) R.drawable.bg_96 else R.drawable.bg_48
        val options = BitmapFactory.Options().apply { inScaled = false }
        val bgBitmap = BitmapFactory.decodeResource(context.resources, bgResId, options)
        val alphaMap = calculateAlphaMap(bgBitmap)
        bgBitmap.recycle()

        // Create a mutable copy to modify
        val resultBitmap = source.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(logoSize * logoSize)
        
        // Read just the watermark part for efficiency, or we can read the whole image,
        // but it's better to getPixels just for the bounding box.
        resultBitmap.getPixels(pixels, 0, logoSize, x, y, logoSize, logoSize)

        for (row in 0 until logoSize) {
            for (col in 0 until logoSize) {
                val index = row * logoSize + col
                var alpha = alphaMap[index]

                if (alpha < ALPHA_THRESHOLD) {
                    continue
                }

                alpha = minOf(alpha, MAX_ALPHA)
                val oneMinusAlpha = 1.0f - alpha

                val pixel = pixels[index]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                val newR = ((r - alpha * LOGO_VALUE) / oneMinusAlpha).toInt().coerceIn(0, 255)
                val newG = ((g - alpha * LOGO_VALUE) / oneMinusAlpha).toInt().coerceIn(0, 255)
                val newB = ((b - alpha * LOGO_VALUE) / oneMinusAlpha).toInt().coerceIn(0, 255)
                // preserve original alpha
                val a = Color.alpha(pixel)

                pixels[index] = Color.argb(a, newR, newG, newB)
            }
        }

        // Write the modified pixels back
        resultBitmap.setPixels(pixels, 0, logoSize, x, y, logoSize, logoSize)

        return resultBitmap
    }
}
