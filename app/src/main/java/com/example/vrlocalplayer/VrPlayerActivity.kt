package com.example.vrlocalplayer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlin.math.roundToInt

class VrPlayerActivity : AppCompatActivity(), StereoGLSurfaceView.GestureListener {

    companion object {
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_DUPLICATE_SBS = "extra_duplicate_sbs"

        private const val TICK_MS = 500L
        private const val SAVE_EVERY_TICKS = 10
        private const val AUTO_HIDE_MS = 4_000L
        private const val SEEK_STEP_MS = 10_000L
    }

    private var player: ExoPlayer? = null
    private lateinit var glView: StereoGLSurfaceView
    private lateinit var currentValues: VrSettings.Values

    private lateinit var videoUri: Uri
    private var videoName = ""

    private val handler = Handler(Looper.getMainLooper())
    private var ticks = 0
    private var userSeeking = false

    private lateinit var timelineBar: View
    private lateinit var btnToggle: ImageButton
    private lateinit var panelScroll: View
    private lateinit var seekTimeline: SeekBar
    private lateinit var tvPosition: TextView
    private lateinit var tvDuration: TextView
    private lateinit var btnPlayPause: ImageButton

    private lateinit var seekZoom: SeekBar
    private lateinit var seekPanX: SeekBar
    private lateinit var seekPanY: SeekBar
    private lateinit var tvZoom: TextView
    private lateinit var tvPanX: TextView
    private lateinit var tvPanY: TextView

    private val tick = object : Runnable {
        override fun run() {
            updateTimeline()
            if (++ticks % SAVE_EVERY_TICKS == 0) saveProgress()
            handler.postDelayed(this, TICK_MS)
        }
    }

    private val autoHide = Runnable { setOverlayVisible(false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()

        setContentView(R.layout.activity_vr_player)
        glView = findViewById(R.id.glSurfaceView)

        // Opened from our own picker, or from another app via Open with / Share
        // (e.g. My Files inside Samsung Secure Folder, or the Files by Google Safe folder).
        val uri = resolveVideoUri(intent)
        if (uri == null) {
            finish()
            return
        }
        videoUri = uri
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
        }
        videoName = PlaybackHistory.displayName(this, uri)

        val requestedDuplicate = intent.getBooleanExtra(EXTRA_DUPLICATE_SBS, true)
        currentValues = VrSettings.load(this, requestedDuplicate)

        glView.init(currentValues.duplicateSbs, currentValues) { surface ->
            startPlayback(videoUri, surface)
        }
        glView.gestureListener = this

        setupControlPanel()
        setupTimeline()
    }

    private fun resolveVideoUri(intent: Intent): Uri? {
        intent.getStringExtra(EXTRA_VIDEO_URI)?.let { return Uri.parse(it) }
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            else -> null
        }
    }

    private fun startPlayback(uri: Uri, surface: Surface) {
        val exoPlayer = ExoPlayer.Builder(this).build().also { player = it }
        exoPlayer.setVideoSurface(surface)
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.repeatMode = ExoPlayer.REPEAT_MODE_ONE
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                btnPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
                scheduleAutoHide()
            }

            override fun onPlayerError(error: PlaybackException) {
                // 2xxx codes are IO errors: usually the file moved or its access grant expired.
                val message = if (error.errorCode in 2000..2999) R.string.file_unavailable else R.string.cannot_play
                Toast.makeText(this@VrPlayerActivity, message, Toast.LENGTH_LONG).show()
                finish()
            }
        })

        val resumeAt = PlaybackHistory.resumePosition(this, uri.toString())
        if (resumeAt > 0) {
            exoPlayer.seekTo(resumeAt)
            Toast.makeText(
                this,
                getString(R.string.resumed_at, PlaybackHistory.formatTime(resumeAt)),
                Toast.LENGTH_SHORT
            ).show()
        }
        PlaybackHistory.record(this, uri.toString(), videoName, resumeAt, 0L)

        exoPlayer.playWhenReady = true
        exoPlayer.prepare()
        handler.post(tick)
    }

    private fun saveProgress() {
        val p = player ?: return
        if (p.playbackState == Player.STATE_IDLE) return
        val duration = if (p.duration == C.TIME_UNSET) 0L else p.duration
        PlaybackHistory.record(this, videoUri.toString(), videoName, p.currentPosition, duration)
    }

    private fun setupTimeline() {
        timelineBar = findViewById(R.id.timelineBar)
        seekTimeline = findViewById(R.id.seekTimeline)
        tvPosition = findViewById(R.id.tvPosition)
        tvDuration = findViewById(R.id.tvDuration)
        btnPlayPause = findViewById(R.id.btnPlayPause)

        btnPlayPause.setOnClickListener {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
            scheduleAutoHide()
        }
        findViewById<ImageButton>(R.id.btnRewind).setOnClickListener { seekBy(-SEEK_STEP_MS) }
        findViewById<ImageButton>(R.id.btnForward).setOnClickListener { seekBy(SEEK_STEP_MS) }

        seekTimeline.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val duration = player?.duration?.takeIf { it != C.TIME_UNSET } ?: return
                tvPosition.text = PlaybackHistory.formatTime(duration * progress / seekTimeline.max)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                userSeeking = true
                handler.removeCallbacks(autoHide)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                userSeeking = false
                val p = player
                val duration = p?.duration?.takeIf { it != C.TIME_UNSET }
                if (p != null && duration != null) {
                    p.seekTo(duration * seekTimeline.progress / seekTimeline.max)
                }
                scheduleAutoHide()
            }
        })

        setOverlayVisible(true)
    }

    private fun seekBy(deltaMs: Long) {
        val p = player ?: return
        val duration = p.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        p.seekTo((p.currentPosition + deltaMs).coerceIn(0L, duration))
        updateTimeline()
        scheduleAutoHide()
    }

    private fun updateTimeline() {
        val p = player ?: return
        val duration = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        tvDuration.text = PlaybackHistory.formatTime(duration)
        seekTimeline.secondaryProgress = (p.bufferedPosition * seekTimeline.max / duration).toInt()
        if (!userSeeking) {
            tvPosition.text = PlaybackHistory.formatTime(p.currentPosition)
            seekTimeline.progress = (p.currentPosition * seekTimeline.max / duration).toInt()
        }
    }

    private fun setOverlayVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        timelineBar.visibility = v
        btnToggle.visibility = v
        if (visible) scheduleAutoHide() else handler.removeCallbacks(autoHide)
    }

    /** Hides the timeline after a few idle seconds, but only while playing and the panel is closed. */
    private fun scheduleAutoHide() {
        handler.removeCallbacks(autoHide)
        val playing = player?.isPlaying == true
        if (playing && !userSeeking && panelScroll.visibility != View.VISIBLE) {
            handler.postDelayed(autoHide, AUTO_HIDE_MS)
        }
    }

    override fun onVideoTap() {
        if (panelScroll.visibility == View.VISIBLE) {
            panelScroll.visibility = View.GONE
            scheduleAutoHide()
            return
        }
        setOverlayVisible(timelineBar.visibility != View.VISIBLE)
    }

    override fun onVideoTransform(scale: Float, panX: Float, panY: Float) {
        currentValues = currentValues.copy(scale = scale, panX = panX, panY = panY)
        refreshViewSliders()
    }

    override fun onVideoTransformEnd() {
        persist()
    }

    private fun refreshViewSliders() {
        seekZoom.progress = fractionToProgress(currentValues.scale, VrSettings.SCALE_MIN, VrSettings.SCALE_MAX, seekZoom.max)
        seekPanX.progress = fractionToProgress(currentValues.panX, VrSettings.PAN_MIN, VrSettings.PAN_MAX, seekPanX.max)
        seekPanY.progress = fractionToProgress(currentValues.panY, VrSettings.PAN_MIN, VrSettings.PAN_MAX, seekPanY.max)
        updateZoomLabel(tvZoom, currentValues.scale)
        updatePercentLabel(tvPanX, currentValues.panX)
        updatePercentLabel(tvPanY, currentValues.panY)
    }

    private fun setupControlPanel() {
        panelScroll = findViewById(R.id.panelScroll)
        btnToggle = findViewById(R.id.btnToggleControls)
        val switchVrMode = findViewById<SwitchMaterial>(R.id.switchVrMode)

        seekZoom = findViewById(R.id.seekZoom)
        seekPanX = findViewById(R.id.seekPanX)
        seekPanY = findViewById(R.id.seekPanY)
        val seekGap = findViewById<SeekBar>(R.id.seekGap)
        val seekIpd = findViewById<SeekBar>(R.id.seekIpd)

        tvZoom = findViewById(R.id.tvZoomValue)
        tvPanX = findViewById(R.id.tvPanXValue)
        tvPanY = findViewById(R.id.tvPanYValue)
        val tvGap = findViewById<TextView>(R.id.tvGapValue)
        val tvIpd = findViewById<TextView>(R.id.tvIpdValue)

        btnToggle.setOnClickListener {
            panelScroll.visibility = if (panelScroll.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            scheduleAutoHide()
        }
        findViewById<MaterialButton>(R.id.btnDoneControls).setOnClickListener {
            panelScroll.visibility = View.GONE
            scheduleAutoHide()
        }

        switchVrMode.isChecked = currentValues.duplicateSbs
        seekZoom.progress = fractionToProgress(currentValues.scale, VrSettings.SCALE_MIN, VrSettings.SCALE_MAX, seekZoom.max)
        seekPanX.progress = fractionToProgress(currentValues.panX, VrSettings.PAN_MIN, VrSettings.PAN_MAX, seekPanX.max)
        seekPanY.progress = fractionToProgress(currentValues.panY, VrSettings.PAN_MIN, VrSettings.PAN_MAX, seekPanY.max)
        seekGap.progress = fractionToProgress(currentValues.gap, VrSettings.GAP_MIN, VrSettings.GAP_MAX, seekGap.max)
        seekIpd.progress = fractionToProgress(currentValues.ipdShift, VrSettings.IPD_SHIFT_MIN, VrSettings.IPD_SHIFT_MAX, seekIpd.max)

        updateZoomLabel(tvZoom, currentValues.scale)
        updatePercentLabel(tvPanX, currentValues.panX)
        updatePercentLabel(tvPanY, currentValues.panY)
        updatePercentLabel(tvGap, currentValues.gap)
        updatePercentLabel(tvIpd, currentValues.ipdShift)

        switchVrMode.setOnCheckedChangeListener { _, isChecked ->
            currentValues = currentValues.copy(duplicateSbs = isChecked)
            glView.stereoRenderer.duplicateSbs = isChecked
            persist()
        }

        seekZoom.setOnSeekBarChangeListener(onChange { progress ->
            val value = progressToFraction(progress, VrSettings.SCALE_MIN, VrSettings.SCALE_MAX, seekZoom.max)
            currentValues = currentValues.copy(scale = value)
            glView.stereoRenderer.scale = value
            updateZoomLabel(tvZoom, value)
            persist()
        })

        seekPanX.setOnSeekBarChangeListener(onChange { progress ->
            val value = progressToFraction(progress, VrSettings.PAN_MIN, VrSettings.PAN_MAX, seekPanX.max)
            currentValues = currentValues.copy(panX = value)
            glView.stereoRenderer.panX = value
            updatePercentLabel(tvPanX, value)
            persist()
        })

        seekPanY.setOnSeekBarChangeListener(onChange { progress ->
            val value = progressToFraction(progress, VrSettings.PAN_MIN, VrSettings.PAN_MAX, seekPanY.max)
            currentValues = currentValues.copy(panY = value)
            glView.stereoRenderer.panY = value
            updatePercentLabel(tvPanY, value)
            persist()
        })

        seekGap.setOnSeekBarChangeListener(onChange { progress ->
            val value = progressToFraction(progress, VrSettings.GAP_MIN, VrSettings.GAP_MAX, seekGap.max)
            currentValues = currentValues.copy(gap = value)
            glView.stereoRenderer.gap = value
            updatePercentLabel(tvGap, value)
            persist()
        })

        seekIpd.setOnSeekBarChangeListener(onChange { progress ->
            val value = progressToFraction(progress, VrSettings.IPD_SHIFT_MIN, VrSettings.IPD_SHIFT_MAX, seekIpd.max)
            currentValues = currentValues.copy(ipdShift = value)
            glView.stereoRenderer.ipdShift = value
            updatePercentLabel(tvIpd, value)
            persist()
        })

        findViewById<MaterialButton>(R.id.btnResetControls).setOnClickListener {
            currentValues = VrSettings.Values(duplicateSbs = currentValues.duplicateSbs)
            glView.stereoRenderer.scale = currentValues.scale
            glView.stereoRenderer.panX = currentValues.panX
            glView.stereoRenderer.panY = currentValues.panY
            glView.stereoRenderer.gap = currentValues.gap
            glView.stereoRenderer.ipdShift = currentValues.ipdShift
            persist()

            seekZoom.progress = fractionToProgress(currentValues.scale, VrSettings.SCALE_MIN, VrSettings.SCALE_MAX, seekZoom.max)
            seekPanX.progress = fractionToProgress(currentValues.panX, VrSettings.PAN_MIN, VrSettings.PAN_MAX, seekPanX.max)
            seekPanY.progress = fractionToProgress(currentValues.panY, VrSettings.PAN_MIN, VrSettings.PAN_MAX, seekPanY.max)
            seekGap.progress = fractionToProgress(currentValues.gap, VrSettings.GAP_MIN, VrSettings.GAP_MAX, seekGap.max)
            seekIpd.progress = fractionToProgress(currentValues.ipdShift, VrSettings.IPD_SHIFT_MIN, VrSettings.IPD_SHIFT_MAX, seekIpd.max)
            updateZoomLabel(tvZoom, currentValues.scale)
            updatePercentLabel(tvPanX, currentValues.panX)
            updatePercentLabel(tvPanY, currentValues.panY)
            updatePercentLabel(tvGap, currentValues.gap)
            updatePercentLabel(tvIpd, currentValues.ipdShift)
        }
    }

    private fun persist() {
        VrSettings.save(this, currentValues)
    }

    private fun onChange(action: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) action(progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    private fun fractionToProgress(value: Float, min: Float, max: Float, maxProgress: Int): Int {
        val t = ((value - min) / (max - min)).coerceIn(0f, 1f)
        return (t * maxProgress).roundToInt()
    }

    private fun progressToFraction(progress: Int, min: Float, max: Float, maxProgress: Int): Float {
        val t = progress.toFloat() / maxProgress.toFloat()
        return min + t * (max - min)
    }

    private fun updateZoomLabel(tv: TextView, scale: Float) {
        tv.text = "${(scale * 100).roundToInt()}%"
    }

    private fun updatePercentLabel(tv: TextView, fraction: Float) {
        val pct = (fraction * 100).roundToInt()
        tv.text = if (pct > 0) "+$pct%" else "$pct%"
    }

    private fun hideSystemUi() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
        saveProgress()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        saveProgress()
        player?.release()
        player = null
        if (::glView.isInitialized) {
            glView.releaseRendererIfReady()
        }
    }
}
