package br.com.medidor.app.domain

/** Minimum photographed calibration span in pixels.
 * Shorter marks amplify errors from imprecise touch placement.
 * This is a usability guard, not a statement of measurement accuracy.
 */
object ReferenceMarkQuality {
    const val MIN_REFERENCE_PIXELS = 20.0

    fun isReadable(spanPixels: Double): Boolean =
        spanPixels.isFinite() && spanPixels >= MIN_REFERENCE_PIXELS
}
