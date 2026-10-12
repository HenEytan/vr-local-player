package com.example.vrlocalplayer

import android.content.Context

object VrSettings {

    private const val PREFS_NAME = "vr_controls"

    private const val KEY_SCALE = "scale"
    private const val KEY_PAN_X = "pan_x"
    private const val KEY_PAN_Y = "pan_y"
    private const val KEY_GAP = "gap"
    private const val KEY_IPD_SHIFT = "ipd_shift"
    private const val KEY_DUPLICATE = "duplicate_sbs"

    const val SCALE_MIN = 0.5f
    const val SCALE_MAX = 4.0f
    const val SCALE_DEFAULT = 1.0f

    const val PAN_MIN = -1.5f
    const val PAN_MAX = 1.5f
    const val PAN_DEFAULT = 0.0f

    const val GAP_MIN = 0.0f
    const val GAP_MAX = 0.3f
    const val GAP_DEFAULT = 0.0f

    const val IPD_SHIFT_MIN = -0.1f
    const val IPD_SHIFT_MAX = 0.1f
    const val IPD_SHIFT_DEFAULT = 0.0f

    data class Values(
        val scale: Float = SCALE_DEFAULT,
        val panX: Float = PAN_DEFAULT,
        val panY: Float = PAN_DEFAULT,
        val gap: Float = GAP_DEFAULT,
        val ipdShift: Float = IPD_SHIFT_DEFAULT,
        val duplicateSbs: Boolean = true
    )

    fun load(context: Context, defaultDuplicateSbs: Boolean): Values {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return Values(
            scale = prefs.getFloat(KEY_SCALE, SCALE_DEFAULT),
            panX = prefs.getFloat(KEY_PAN_X, PAN_DEFAULT),
            panY = prefs.getFloat(KEY_PAN_Y, PAN_DEFAULT),
            gap = prefs.getFloat(KEY_GAP, GAP_DEFAULT),
            ipdShift = prefs.getFloat(KEY_IPD_SHIFT, IPD_SHIFT_DEFAULT),
            duplicateSbs = prefs.getBoolean(KEY_DUPLICATE, defaultDuplicateSbs)
        )
    }

    fun save(context: Context, values: Values) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_SCALE, values.scale)
            .putFloat(KEY_PAN_X, values.panX)
            .putFloat(KEY_PAN_Y, values.panY)
            .putFloat(KEY_GAP, values.gap)
            .putFloat(KEY_IPD_SHIFT, values.ipdShift)
            .putBoolean(KEY_DUPLICATE, values.duplicateSbs)
            .apply()
    }
}
