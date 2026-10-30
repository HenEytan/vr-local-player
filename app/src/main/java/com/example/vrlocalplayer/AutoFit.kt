package com.example.vrlocalplayer

/**
 * Picks a starting framing for each video: the whole picture at its true shape,
 * centered in each eye, and duplication off when the file already holds both eyes.
 */
object AutoFit {

    enum class Layout { MONO, HALF_SBS, FULL_SBS }

    /** Fraction of the eye viewport a mono video fills in VR mode, so its edges stay clear of the lens rim. */
    const val LENS_MARGIN = 0.9f

    // A frame this wide (32:9 is 3.56) is two side-by-side 16:9-ish eyes, never one picture.
    private const val FULL_SBS_MIN_ASPECT = 3.0f
    private const val HALF_SBS_SQUEEZE = 2f
    private const val VIEW_FIELDS = 3

    private val FULL_SBS_TAG = Regex("(?i)(^|[^a-z0-9])(fsbs|full[-_ ]?sbs)([^a-z0-9]|$)")
    private val HALF_SBS_TAG = Regex("(?i)(^|[^a-z0-9])(h?sbs|half[-_ ]?sbs|lr)([^a-z0-9]|$)")

    data class View(val scale: Float, val panX: Float, val panY: Float) {
        fun encode(): String = "$scale,$panX,$panY"

        companion object {
            fun decode(raw: String?): View? {
                val parts = raw?.split(',') ?: return null
                if (parts.size != VIEW_FIELDS) return null
                val (scale, panX, panY) = parts.map { it.toFloatOrNull() ?: return null }
                return View(
                    scale = scale.coerceIn(VrSettings.SCALE_MIN, VrSettings.SCALE_MAX),
                    panX = panX.coerceIn(VrSettings.PAN_MIN, VrSettings.PAN_MAX),
                    panY = panY.coerceIn(VrSettings.PAN_MIN, VrSettings.PAN_MAX)
                )
            }
        }
    }

    data class Start(val duplicateSbs: Boolean, val view: View)

    fun layoutOf(width: Int, height: Int, pixelRatio: Float, name: String): Layout {
        if (width <= 0 || height <= 0) return Layout.MONO
        if (width * pixelRatio / height >= FULL_SBS_MIN_ASPECT) return Layout.FULL_SBS
        if (FULL_SBS_TAG.containsMatchIn(name)) return Layout.FULL_SBS
        if (HALF_SBS_TAG.containsMatchIn(name)) return Layout.HALF_SBS
        return Layout.MONO
    }

    /** Display aspect of the whole frame, or 0 when the size is not known yet. */
    fun contentAspect(width: Int, height: Int, pixelRatio: Float, layout: Layout): Float {
        if (width <= 0 || height <= 0 || pixelRatio <= 0f) return 0f
        val aspect = width * pixelRatio / height
        // Half SBS squeezes each eye to half width; stretch it back so both eyes keep their shape.
        return if (layout == Layout.HALF_SBS) aspect * HALF_SBS_SQUEEZE else aspect
    }

    /** Half-extents in NDC that fit [contentAspect] inside a viewport of [viewportAspect], letterboxed. */
    fun fitExtent(contentAspect: Float, viewportAspect: Float): Pair<Float, Float> {
        if (contentAspect <= 0f || viewportAspect <= 0f) return 1f to 1f
        return if (contentAspect >= viewportAspect) {
            1f to viewportAspect / contentAspect
        } else {
            contentAspect / viewportAspect to 1f
        }
    }

    fun startingView(layout: Layout, duplicateSbs: Boolean): Start {
        if (layout != Layout.MONO) {
            // Played flat, each half of the frame already lands in its own eye; scaling would pull them together.
            return Start(duplicateSbs = false, view = View(scale = 1f, panX = 0f, panY = 0f))
        }
        val scale = if (duplicateSbs) LENS_MARGIN else 1f
        return Start(duplicateSbs = duplicateSbs, view = View(scale = scale, panX = 0f, panY = 0f))
    }
}
