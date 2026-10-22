package br.com.medidor.app.ui

import androidx.camera.core.ImageProxy
import kotlin.math.max

/**
 * Samples an ImageProxy YUV_420_888 frame directly into a small, upright RGB
 * array. No full-size Bitmap conversion, no media storage, no network.
 */
internal data class LiveRgbFrame(val width: Int, val height: Int, val pixels: IntArray)

internal object LiveCameraFrameSampler {
    fun sample(image: ImageProxy): LiveRgbFrame? {
        val planes = image.planes
        if (planes.size < 3) return null
        val rotation = image.imageInfo.rotationDegrees
        if (rotation !in listOf(0,90,180,270)) return null
        val sourceWidth = image.width
        val sourceHeight = image.height
        if (sourceWidth <= 0 || sourceHeight <= 0) return null
        val uprightWidth = if (rotation == 90 || rotation == 270) sourceHeight else sourceWidth
        val uprightHeight = if (rotation == 90 || rotation == 270) sourceWidth else sourceHeight
        val scale = 192.0 / max(uprightWidth,uprightHeight)
        val width = (uprightWidth * scale).toInt().coerceIn(8,192)
        val height = (uprightHeight * scale).toInt().coerceIn(8,192)
        val out = IntArray(width * height)
        val y = planes[0]
        val u = planes[1]
        val v = planes[2]
        val yy = y.buffer
        val uu = u.buffer
        val vv = v.buffer
        try {
            for (row in 0 until height) for (col in 0 until width) {
                val rx = ((col+0.5)*uprightWidth/width).toInt().coerceIn(0,uprightWidth-1)
                val ry = ((row+0.5)*uprightHeight/height).toInt().coerceIn(0,uprightHeight-1)
                val sx: Int
                val sy: Int
                when (rotation) {
                    90 -> { sx=ry; sy=sourceHeight-1-rx }
                    180 -> { sx=sourceWidth-1-rx; sy=sourceHeight-1-ry }
                    270 -> { sx=sourceWidth-1-ry; sy=rx }
                    else -> { sx=rx; sy=ry }
                }
                val luma = yy.get(sy*y.rowStride+sx*y.pixelStride).toInt() and 255
                val chromaU = (uu.get((sy/2)*u.rowStride+(sx/2)*u.pixelStride).toInt() and 255)-128
                val chromaV = (vv.get((sy/2)*v.rowStride+(sx/2)*v.pixelStride).toInt() and 255)-128
                val c = (luma-16).coerceAtLeast(0)
                val r = ((298*c+409*chromaV+128) shr 8).coerceIn(0,255)
                val g = ((298*c-100*chromaU-208*chromaV+128) shr 8).coerceIn(0,255)
                val b = ((298*c+516*chromaU+128) shr 8).coerceIn(0,255)
                out[row*width+col] = -0x1000000 or (r shl 16) or (g shl 8) or b
            }
        } catch (_: IndexOutOfBoundsException) {
            return null
        }
        return LiveRgbFrame(width,height,out)
    }
}
