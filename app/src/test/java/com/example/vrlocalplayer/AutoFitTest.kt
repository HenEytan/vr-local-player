package com.example.vrlocalplayer

import com.example.vrlocalplayer.AutoFit.Layout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoFitTest {

    private val eps = 1e-4f

    // layoutOf

    @Test
    fun `plain 16x9 video is mono`() {
        assertEquals(Layout.MONO, AutoFit.layoutOf(1920, 1080, 1f, "holiday.mp4"))
    }

    @Test
    fun `32x9 frame is full side by side`() {
        assertEquals(Layout.FULL_SBS, AutoFit.layoutOf(3840, 1080, 1f, "clip.mp4"))
    }

    @Test
    fun `sbs tag in a 16x9 file name is half side by side`() {
        assertEquals(Layout.HALF_SBS, AutoFit.layoutOf(1920, 1080, 1f, "Movie.2019.3D.HSBS.mkv"))
        assertEquals(Layout.HALF_SBS, AutoFit.layoutOf(1920, 1080, 1f, "trip_sbs.mp4"))
        assertEquals(Layout.HALF_SBS, AutoFit.layoutOf(1920, 1080, 1f, "dive_180_LR.mp4"))
    }

    @Test
    fun `full sbs tag keeps the frame shape`() {
        assertEquals(Layout.FULL_SBS, AutoFit.layoutOf(3840, 2160, 1f, "concert.FSBS.mp4"))
        assertEquals(Layout.FULL_SBS, AutoFit.layoutOf(3840, 2160, 1f, "concert full-sbs.mp4"))
    }

    @Test
    fun `sbs inside another word is not a tag`() {
        assertEquals(Layout.MONO, AutoFit.layoutOf(1920, 1080, 1f, "gatsbsy.mp4"))
        assertEquals(Layout.MONO, AutoFit.layoutOf(1920, 1080, 1f, "colrs.mp4"))
    }

    @Test
    fun `anamorphic pixels count toward the wide frame check`() {
        // 1920x1080 stored, pixels twice as wide as tall: displays 3840x1080.
        assertEquals(Layout.FULL_SBS, AutoFit.layoutOf(1920, 1080, 2f, "clip.mp4"))
    }

    @Test
    fun `unknown size is mono`() {
        assertEquals(Layout.MONO, AutoFit.layoutOf(0, 0, 1f, ""))
    }

    // contentAspect

    @Test
    fun `mono aspect is width over height times pixel ratio`() {
        assertEquals(16f / 9f, AutoFit.contentAspect(1920, 1080, 1f, Layout.MONO), eps)
        assertEquals(32f / 9f, AutoFit.contentAspect(1920, 1080, 2f, Layout.MONO), eps)
    }

    @Test
    fun `half sbs unsqueezes each eye`() {
        assertEquals(32f / 9f, AutoFit.contentAspect(1920, 1080, 1f, Layout.HALF_SBS), eps)
    }

    @Test
    fun `full sbs keeps the frame aspect`() {
        assertEquals(32f / 9f, AutoFit.contentAspect(3840, 1080, 1f, Layout.FULL_SBS), eps)
    }

    @Test
    fun `unknown size has no aspect`() {
        assertEquals(0f, AutoFit.contentAspect(0, 1080, 1f, Layout.MONO), eps)
        assertEquals(0f, AutoFit.contentAspect(1920, 0, 1f, Layout.MONO), eps)
        assertEquals(0f, AutoFit.contentAspect(1920, 1080, 0f, Layout.MONO), eps)
    }

    // fitExtent

    @Test
    fun `wide video in a square eye fills the width`() {
        val (x, y) = AutoFit.fitExtent(16f / 9f, 1f)
        assertEquals(1f, x, eps)
        assertEquals(9f / 16f, y, eps)
    }

    @Test
    fun `tall video in a wide screen fills the height`() {
        val (x, y) = AutoFit.fitExtent(9f / 16f, 2f)
        assertEquals((9f / 16f) / 2f, x, eps)
        assertEquals(1f, y, eps)
    }

    @Test
    fun `matching shapes fill the viewport`() {
        val (x, y) = AutoFit.fitExtent(1.5f, 1.5f)
        assertEquals(1f, x, eps)
        assertEquals(1f, y, eps)
    }

    @Test
    fun `unknown shapes fill the viewport`() {
        val (x, y) = AutoFit.fitExtent(0f, 1.5f)
        assertEquals(1f, x, eps)
        assertEquals(1f, y, eps)
    }

    // startingView

    @Test
    fun `mono video in vr mode is centered inside the lens margin`() {
        val start = AutoFit.startingView(Layout.MONO, duplicateSbs = true)
        assertEquals(true, start.duplicateSbs)
        assertEquals(AutoFit.LENS_MARGIN, start.view.scale, eps)
        assertEquals(0f, start.view.panX, eps)
        assertEquals(0f, start.view.panY, eps)
    }

    @Test
    fun `mono video played flat fills the screen`() {
        val start = AutoFit.startingView(Layout.MONO, duplicateSbs = false)
        assertEquals(false, start.duplicateSbs)
        assertEquals(1f, start.view.scale, eps)
    }

    @Test
    fun `side by side video turns duplication off`() {
        for (layout in listOf(Layout.HALF_SBS, Layout.FULL_SBS)) {
            val start = AutoFit.startingView(layout, duplicateSbs = true)
            assertEquals(false, start.duplicateSbs)
            // Already split per eye: scaling would pull the two eyes toward each other.
            assertEquals(1f, start.view.scale, eps)
        }
    }

    // View encode/decode

    @Test
    fun `view survives encode and decode`() {
        val view = AutoFit.View(scale = 1.25f, panX = -0.1f, panY = 0.3f)
        assertEquals(view, AutoFit.View.decode(view.encode()))
    }

    @Test
    fun `broken saved view decodes to null`() {
        assertNull(AutoFit.View.decode(null))
        assertNull(AutoFit.View.decode(""))
        assertNull(AutoFit.View.decode("1.0,abc,0"))
        assertNull(AutoFit.View.decode("1.0,0"))
    }

    @Test
    fun `decoded view is clamped to the slider ranges`() {
        val view = AutoFit.View.decode("99,-9,9")!!
        assertEquals(VrSettings.SCALE_MAX, view.scale, eps)
        assertEquals(VrSettings.PAN_MIN, view.panX, eps)
        assertEquals(VrSettings.PAN_MAX, view.panY, eps)
    }
}
