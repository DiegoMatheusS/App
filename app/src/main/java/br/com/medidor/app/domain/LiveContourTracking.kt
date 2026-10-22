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
