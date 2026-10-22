package br.com.medidor.app.domain

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Conservative image-space consistency check for color-segmented live frames.
 * Does NOT identify objects, track their true identity, or measure distance.
 */
data class LiveContourState(
    val result: PlanarContourResult?,
    val label: String
)

object LiveContourTracking {
    /**
     * Move the next frame's seed approximately with the accepted bounding box.
     * Keeps the picked region near the old object, but does not track identity.
     */
    fun followSeed(
        previous: PlanarContourResult, current: PlanarContourResult,
        previousSeed: NormalizedPoint
    ): NormalizedPoint? {
        if (check(previous,current).result == null) return null
        val dx = ((current.minX + current.maxX).toFloat() / (2 * current.width)) -
            ((previous.minX + previous.maxX).toFloat() / (2 * previous.width))
        val dy = ((current.minY + current.maxY).toFloat() / (2 * current.height)) -
            ((previous.minY + previous.maxY).toFloat() / (2 * previous.height))
        val x = (previousSeed.x+dx).coerceIn(0f,1f)
        val y = (previousSeed.y+dy).coerceIn(0f,1f)
        val left = current.minX.toFloat()/current.width
        val right = (current.maxX+1f)/current.width
        val top = current.minY.toFloat()/current.height
        val bottom = (current.maxY+1f)/current.height
        // If the translated seed landed outside the region, choose center.
        return if (x in left..right && y in top..bottom) NormalizedPoint(x,y)
        else NormalizedPoint((left+right)/2,(top+bottom)/2)
    }

    fun check(previous: PlanarContourResult?, current: PlanarContourResult?): LiveContourState {
        if (current == null) return LiveContourState(null, "Região não encontrada; toque novamente no objeto.")
        val areaFraction = current.pixelsInside.toDouble() / (current.width * current.height)
        if (areaFraction < .002 || areaFraction > .65)
            return LiveContourState(null, "Região muito pequena ou grande para acompanhar.")
        val prev = previous ?: return LiveContourState(current, "Contorno aproximado encontrado.")
        val cx = (current.minX + current.maxX).toDouble() / (2 * current.width)
        val cy = (current.minY + current.maxY).toDouble() / (2 * current.height)
        val px = (prev.minX + prev.maxX).toDouble() / (2 * prev.width)
        val py = (prev.minY + prev.maxY).toDouble() / (2 * prev.height)
        val move = hypot(cx-px,cy-py)
        val ratio = areaFraction / (prev.pixelsInside.toDouble()/(prev.width*prev.height))
        if (move > .18 || ratio !in .45..2.2)
            return LiveContourState(null, "Contorno instável; selecione novamente o objeto.")
        return LiveContourState(current, "Contorno aproximado atualizado.")
    }
}
