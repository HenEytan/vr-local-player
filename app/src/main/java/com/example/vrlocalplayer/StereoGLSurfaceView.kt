package com.example.vrlocalplayer

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector

class StereoGLSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    /** Callbacks for touch gestures on the video. */
    interface GestureListener {
        fun onVideoTap()
        fun onVideoTransform(scale: Float, panX: Float, panY: Float)
        fun onVideoTransformEnd()
    }

    lateinit var stereoRenderer: StereoRenderer
        private set

    var gestureListener: GestureListener? = null

    private var transforming = false

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoomAround(detector.focusX, detector.focusY, detector.scaleFactor)
                return true
            }
        })

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                gestureListener?.onVideoTap()
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                panBy(-dx, -dy)
                return true
            }
        })

    fun init(
        duplicateSbs: Boolean,
        initialSettings: VrSettings.Values,
        onSurfaceReady: (android.view.Surface) -> Unit
    ) {
        setEGLContextClientVersion(2)
        stereoRenderer = StereoRenderer(duplicateSbs, initialSettings, onSurfaceReady)
        setRenderer(stereoRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun releaseRendererIfReady() {
        if (::stereoRenderer.isInitialized) {
            stereoRenderer.release()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!::stereoRenderer.isInitialized) return super.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            if (transforming) {
                transforming = false
                gestureListener?.onVideoTransformEnd()
            }
        }
        return true
    }

    /** Width in pixels of one eye's viewport (the whole view in flat mode). */
    private fun eyeWidth(): Float {
        val w = width.toFloat()
        if (!stereoRenderer.duplicateSbs) return w
        val gapPx = (stereoRenderer.gap.coerceIn(0f, 0.9f) * w).toInt()
        return ((w - gapPx) / 2f).coerceAtLeast(1f)
    }

    /**
     * Zooms by [factor] keeping the video point under the fingers fixed. The focal point is
     * mapped into the viewport of the eye that was touched, so pinching on either half works.
     */
    private fun zoomAround(focusX: Float, focusY: Float, factor: Float) {
        val r = stereoRenderer
        val eyeW = eyeWidth()
        val eyeLeft = if (r.duplicateSbs && focusX > width / 2f) width - eyeW else 0f
        val eyeShift = when {
            !r.duplicateSbs -> 0f
            focusX > width / 2f -> -r.ipdShift
            else -> r.ipdShift
        }
        // Focal point in the eye's normalized device coordinates (-1..1, y up).
        val fx = ((focusX - eyeLeft) / eyeW) * 2f - 1f
        val fy = 1f - (focusY / height.toFloat()) * 2f

        val newScale = (r.scale * factor).coerceIn(VrSettings.SCALE_MIN, VrSettings.SCALE_MAX)
        val k = newScale / r.scale
        // On screen a point p of the quad lands at scale * p + 2 * (pan + eyeShift).
        // Scaling by k about f: newOffset = k * offset + f * (1 - k).
        val offsetX = r.panX + eyeShift
        val newPanX = k * offsetX + fx * (1f - k) / 2f - eyeShift
        val newPanY = k * r.panY + fy * (1f - k) / 2f
        apply(newScale, newPanX, newPanY)
    }

    private fun panBy(dxPx: Float, dyPx: Float) {
        val r = stereoRenderer
        // One eye's viewport spans 2 NDC units and pan is applied as 2 * pan.
        apply(r.scale, r.panX + dxPx / eyeWidth(), r.panY - dyPx / height.toFloat())
    }

    private fun apply(scale: Float, panX: Float, panY: Float) {
        val r = stereoRenderer
        r.scale = scale
        r.panX = panX.coerceIn(VrSettings.PAN_MIN, VrSettings.PAN_MAX)
        r.panY = panY.coerceIn(VrSettings.PAN_MIN, VrSettings.PAN_MAX)
        transforming = true
        gestureListener?.onVideoTransform(r.scale, r.panX, r.panY)
    }
}
