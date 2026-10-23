package br.com.medidor.app.domain

import kotlin.math.abs

data class CountRegion(val left: Int, val top: Int, val right: Int, val bottom: Int, val area: Int)

/** Experimental foreground components against the average image border color. Offline only.
 * Not semantic object detection: joined objects may form one component.
 */
object LiveObjectCounter {
    fun detect(pixels: IntArray, width: Int, height: Int, sensitivity: Int = 45): List<CountRegion> {
        require(width in 8..512 && height in 8..512 && pixels.size == width * height)
        require(sensitivity in 15..100)
        var red = 0L; var green = 0L; var blue = 0L; var samples = 0L
        for (y in 0 until height) for (x in 0 until width) {
            if (x < 3 || y < 3 || x >= width - 3 || y >= height - 3) {
                val pixel = pixels[y * width + x]
                red += (pixel ushr 16) and 255
                green += (pixel ushr 8) and 255
                blue += pixel and 255
                samples++
            }
        }
        val bgR = (red / samples).toInt()
        val bgG = (green / samples).toInt()
        val bgB = (blue / samples).toInt()
        val foreground = BooleanArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val difference = maxOf(abs(((p ushr 16) and 255) - bgR),
                abs(((p ushr 8) and 255) - bgG), abs((p and 255) - bgB))
            foreground[i] = difference >= sensitivity
        }
        val seen = BooleanArray(pixels.size)
        val queue = IntArray(pixels.size)
        val regions = ArrayList<CountRegion>()
        val minimumArea = maxOf(24, pixels.size / 600)
        for (start in foreground.indices) {
            if (!foreground[start] || seen[start]) continue
            var head = 0; var tail = 1; queue[0] = start; seen[start] = true
            var minX = width; var minY = height; var maxX = 0; var maxY = 0
            var area = 0
            while (head < tail) {
                val point = queue[head++]
                val x = point % width; val y = point / width
                minX = minOf(minX, x); maxX = maxOf(maxX, x)
                minY = minOf(minY, y); maxY = maxOf(maxY, y); area++
                fun visit(index: Int) {
                    if (foreground[index] && !seen[index]) {
                        seen[index] = true; queue[tail++] = index
                    }
                }
                if (x > 0) visit(point - 1)
                if (x + 1 < width) visit(point + 1)
                if (y > 0) visit(point - width)
                if (y + 1 < height) visit(point + width)
            }
            val touchesBorder = minX < 2 || minY < 2 ||
                maxX >= width - 2 || maxY >= height - 2
            if (!touchesBorder && area >= minimumArea && area <= pixels.size / 3 &&
                (maxX - minX) >= 3 && (maxY - minY) >= 3) {
                regions.add(CountRegion(minX, minY, maxX, maxY, area))
            }
        }
        return regions.sortedByDescending { it.area }.take(100)
    }
}
