package br.com.medidor.app.domain

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class RectifiedSize(val width: Int, val height: Int)

/**
 * Users must select corners of a flat, rectangular sheet clockwise:
 * upper-left, upper-right, lower-right, lower-left.
 * Output aspect ratio comes from the sheet's KNOWN width / height.
 */
object PlanRectification {
    fun targetSize(
        corners: List<PlanPoint>,
        imageWidth: Int,
        imageHeight: Int,
        sheetWidthCm: Double,
        sheetHeightCm: Double
    ): RectifiedSize? {
        if (corners.size != 4 || imageWidth < 32 || imageHeight < 32 ||
            !sheetWidthCm.isFinite() || !sheetHeightCm.isFinite() ||
            sheetWidthCm <= 0 || sheetHeightCm <= 0) return null

        val aspect = sheetWidthCm / sheetHeightCm
        if (!aspect.isFinite() || aspect !in .2..5.0) return null

        // Convex, non-self-intersecting quadrilateral of meaningful size.
        fun cross(a: PlanPoint, b: PlanPoint, c: PlanPoint): Double =
            (b.x - a.x).toDouble() * (c.y - b.y) -
                (b.y - a.y).toDouble() * (c.x - b.x)
        val turns = (0..3).map { i ->
            cross(corners[i], corners[(i + 1) % 4], corners[(i + 2) % 4])
        }
        if (turns.any { !it.isFinite() || it < .0008 }) return null
        val area = kotlin.math.abs((0..3).sumOf { i ->
            val a = corners[i]
            val b = corners[(i + 1) % 4]
            a.x.toDouble() * b.y - b.x.toDouble() * a.y
        }) / 2
        if (area < .025) return null

        fun length(a: PlanPoint, b: PlanPoint) =
            hypot((a.x - b.x).toDouble() * imageWidth, (a.y - b.y).toDouble() * imageHeight)
        val rawWidth = max(length(corners[0], corners[1]), length(corners[3], corners[2]))
        val rawHeight = rawWidth / aspect
        val ratio = min(2048.0 / rawWidth, 2048.0 / rawHeight).coerceAtMost(1.0)
        val width = (rawWidth * ratio).roundToInt().coerceIn(64, 2048)
        val height = (width / aspect).roundToInt().coerceIn(64, 2048)
        return RectifiedSize(width, height)
    }
}

/** Matches a chosen fixed architectural point on the ground floor and upper floor. */
object PlanLandmarkAlignment {
    fun suggestedOffset(base: PlanFloor, upper: PlanFloor): Pair<Double, Double>? {
        val p0 = base.alignmentPoint ?: return null
        val p1 = upper.alignmentPoint ?: return null
        val baseWorld = FloorPlanMath.worldPointMeters(base, p0) ?: return null
        val upperWorld = FloorPlanMath.worldPointMeters(upper, p1) ?: return null
        val baseCenter = FloorPlanMath.worldPointMeters(base, PlanPoint(.5f, .5f)) ?: return null
        val upperCenter = FloorPlanMath.worldPointMeters(upper, PlanPoint(.5f, .5f)) ?: return null
        val x = (baseWorld.first - baseCenter.first + base.offsetXMeters) -
            (upperWorld.first - upperCenter.first)
        val y = (baseWorld.second - baseCenter.second + base.offsetYMeters) -
            (upperWorld.second - upperCenter.second)
        return if (x.isFinite() && y.isFinite()) x to y else null
    }
}
