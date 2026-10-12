package com.example.vrlocalplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private var selectedUri: Uri? = null

    // OpenDocument (not GetContent) so the read grant can be persisted and
    // "Continue watching" still opens the file after the app restarts.
    private val pickVideoLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                }
                selectVideo(uri, PlaybackHistory.displayName(this, uri))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<MaterialButton>(R.id.btnPickVideo).setOnClickListener {
            pickVideoLauncher.launch(arrayOf("video/*"))
        }

        findViewById<MaterialButton>(R.id.btnPlay).setOnClickListener {
            val uri = selectedUri ?: return@setOnClickListener
            play(uri)
        }
    }

    override fun onResume() {
        super.onResume()
        renderRecents()
    }

    private fun selectVideo(uri: Uri, name: String) {
        selectedUri = uri
        findViewById<TextView>(R.id.tvSelectedFile).text = name
        findViewById<MaterialButton>(R.id.btnPlay).isEnabled = true
    }

    private fun play(uri: Uri) {
        val duplicateSbs = findViewById<SwitchMaterial>(R.id.switchDuplicateSbs).isChecked
        val intent = Intent(this, VrPlayerActivity::class.java).apply {
            putExtra(VrPlayerActivity.EXTRA_VIDEO_URI, uri.toString())
            putExtra(VrPlayerActivity.EXTRA_DUPLICATE_SBS, duplicateSbs)
        }
        startActivity(intent)
    }

    private fun renderRecents() {
        val card = findViewById<View>(R.id.recentCard)
        val list = findViewById<LinearLayout>(R.id.recentList)
        list.removeAllViews()

        val entries = PlaybackHistory.all(this)
        card.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE

        val inflater = LayoutInflater.from(this)
        entries.forEach { entry ->
            val row = inflater.inflate(R.layout.item_recent, list, false)
            val uri = Uri.parse(entry.uri)
            val name = entry.name.ifBlank { uri.lastPathSegment ?: entry.uri }

            row.findViewById<TextView>(R.id.tvRecentName).text = name
            row.findViewById<TextView>(R.id.tvRecentTime).text = if (entry.durationMs > 0) {
                getString(
                    R.string.recent_progress,
                    PlaybackHistory.formatTime(entry.positionMs),
                    PlaybackHistory.formatTime(entry.durationMs)
                )
            } else {
                PlaybackHistory.formatTime(entry.positionMs)
            }
            val progress = row.findViewById<ProgressBar>(R.id.progressRecent)
            progress.progress = if (entry.durationMs > 0) {
                (entry.positionMs * progress.max / entry.durationMs).toInt()
            } else 0

            row.setOnClickListener {
                selectVideo(uri, name)
                play(uri)
            }
            row.setOnLongClickListener {
                PlaybackHistory.remove(this, entry.uri)
                try {
                    contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {
                }
                Toast.makeText(this, R.string.recent_removed, Toast.LENGTH_SHORT).show()
                renderRecents()
                true
            }
            list.addView(row)
        }

        // Preselect the most recent video so Play works straight away.
        if (selectedUri == null) {
            entries.firstOrNull()?.let { selectVideo(Uri.parse(it.uri), it.name) }
        }
    }
}
