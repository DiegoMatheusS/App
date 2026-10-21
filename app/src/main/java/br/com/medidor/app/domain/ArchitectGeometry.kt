package br.com.medidor.app.domain

import kotlin.math.hypot

/**
 * Geometry of a photographed floor plan. The image must be orthogonal (top-down)
 * and the reference/walls must be on the same flat surface. Otherwise the lengths
 * and the preview are approximations, not engineering measurements.
 */
data class PlanPoint(val x: Float, val y: Float) {
    init {
        require(x.isFinite() && y.isFinite() && x in 0f..1f && y in 0f..1f)
    }
}

data class PlanWall(val start: PlanPoint, val end: PlanPoint)

data class PlanFloor(
    val title: String,
    val imageFile: String? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val knownMeters: Double = 4.0,
    val referenceStart: PlanPoint? = null,
    val referenceEnd: PlanPoint? = null,
    val wallHeightMeters: Double = 2.7,
    val walls: List<PlanWall> = emptyList()
) {
    val calibrated: Boolean get() = FloorPlanMath.pixelsPerMeter(this) != null
}

data class ArchitectPlan(
    val floors: List<PlanFloor> = listOf(PlanFloor("Térreo")),
    val selectedFloor: Int = 0
)

object FloorPlanMath {
    fun pointDistancePx(a: PlanPoint, b: PlanPoint, width: Int, height: Int): Double {
        require(width > 0 && height > 0)
        return hypot((a.x - b.x).toDouble() * width, (a.y - b.y).toDouble() * height)
    }

    /** Null = invalid scale; avoid outputting misleading measurements. */
    fun pixelsPerMeter(floor: PlanFloor): Double? {
        val a = floor.referenceStart ?: return null
        val b = floor.referenceEnd ?: return null
        if (floor.imageWidth <= 0 || floor.imageHeight <= 0 ||
            !floor.knownMeters.isFinite() || floor.knownMeters <= 0.0) return null
        val pixels = pointDistancePx(a, b, floor.imageWidth, floor.imageHeight)
        return if (pixels > 1.0) pixels / floor.knownMeters else null
    }

    fun wallLengthMeters(floor: PlanFloor, wall: PlanWall): Double? =
        pixelsPerMeter(floor)?.let { pxPerMeter ->
            pointDistancePx(wall.start, wall.end, floor.imageWidth, floor.imageHeight) / pxPerMeter
        }

    fun totalWallMeters(floor: PlanFloor): Double? {
        if (!floor.calibrated) return null
        return floor.walls.sumOf { wallLengthMeters(floor, it) ?: 0.0 }
    }

    /** Image normalized point into rough world x/y coordinates in meters. */
    fun worldPointMeters(floor: PlanFloor, point: PlanPoint): Pair<Double, Double>? {
        val pxPerMeter = pixelsPerMeter(floor) ?: return null
        return (point.x.toDouble() * floor.imageWidth / pxPerMeter) to
            (point.y.toDouble() * floor.imageHeight / pxPerMeter)
    }
}
