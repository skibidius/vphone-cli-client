package com.vphone.client

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.vphone.client.stream.StreamController

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var root: FrameLayout
    private lateinit var surface: SurfaceView
    private lateinit var status: TextView

    private var controller: StreamController? = null
    private val touchInjector = TouchInjector()

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("vphone", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        root = findViewById(R.id.root)
        surface = findViewById(R.id.surface)
        status = findViewById(R.id.status)

        surface.holder.addCallback(this)
        surface.setOnTouchListener { v, event -> touchInjector.handle(controller, event, v.width, v.height) }

        // Opened via the launcher long-press shortcut.
        if (intent?.getBooleanExtra(EXTRA_OPEN_SETTINGS, false) == true) {
            window.decorView.post { showSettings() }
        }

        // Android back gesture/button must not leave the app (iOS back is an
        // edge swipe that is sent to the VM as touch).
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Consumed.
            }
        })

        // Let edge/bottom swipes reach the VM instead of Android gestures.
        root.viewTreeObserver.addOnGlobalLayoutListener {
            excludeSystemGestures()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)) {
            showSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        // Pin the screen (kiosk mode) so the home gesture can't leave the app.
        // Needs "Screen pinning" enabled in Settings → Security.
        try {
            startLockTask()
        } catch (_: Exception) {
            // Screen pinning unavailable — home gesture will still exit.
        }
    }

    private fun excludeSystemGestures() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && root.width > 0 && root.height > 0) {
            root.systemGestureExclusionRects = listOf(Rect(0, 0, root.width, root.height))
        }
    }

    // ---- Settings ---------------------------------------------------------

    private fun host(): String = prefs.getString("host", "127.0.0.1") ?: "127.0.0.1"
    private fun port(): Int = prefs.getInt("port", 8765)
    private fun token(): String = prefs.getString("token", "vphone-android-bridge") ?: "vphone-android-bridge"

    private fun newController(): StreamController = StreamController(host(), port(), token())

    private fun showSettings() {
        val hostEdit = EditText(this).apply { setText(host()); hint = "host" }
        val portEdit = EditText(this).apply { setText(port().toString()); hint = "port" }
        val tokenEdit = EditText(this).apply { setText(token()); hint = "token" }

        val container = androidx.appcompat.widget.LinearLayoutCompat(this).apply {
            orientation = androidx.appcompat.widget.LinearLayoutCompat.VERTICAL
            setPadding(48, 24, 48, 0)
            addView(hostEdit)
            addView(portEdit)
            addView(tokenEdit)
        }

        AlertDialog.Builder(this)
            .setTitle("Connection")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit()
                    .putString("host", hostEdit.text.toString().trim())
                    .putInt("port", portEdit.text.toString().toIntOrNull() ?: 8765)
                    .putString("token", tokenEdit.text.toString().trim())
                    .apply()
                reconnect()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun reconnect() {
        controller?.stop()
        if (surface.holder.surface.isValid) {
            controller = newController()
            controller?.start(surface.holder.surface, { _, _ -> }, ::onStatus)
        }
    }

    // Touch (single- and multi-touch) is handled by TouchInjector; see the
    // setOnTouchListener call in onCreate.

    // ---- Hardware volume keys ---------------------------------------------

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                controller?.sendButton("volume-up")
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                controller?.sendButton("volume-down")
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    // ---- Surface lifecycle ------------------------------------------------

    override fun surfaceCreated(holder: SurfaceHolder) {
        controller = newController()
        controller?.start(holder.surface, { _, _ -> }, ::onStatus)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        controller?.stop()
        controller = null
    }

    // ---- Status ------------------------------------------------------------

    // The SurfaceView fills the whole screen (match_parent in the layout), so
    // the video stretches to cover it without letterbox bars. The slight
    // distortion is imperceptible for the close iPhone/Android aspect ratios,
    // and it keeps the normalized touch mapping (view == video) correct.

    private fun onStatus(text: String) {
        runOnUiThread {
            if (text == "Streaming") {
                status.visibility = View.GONE
            } else {
                status.text = text
                status.visibility = View.VISIBLE
            }
        }
    }

    private fun hideSystemBars() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
    }

    companion object {
        const val EXTRA_OPEN_SETTINGS = "com.vphone.client.EXTRA_OPEN_SETTINGS"
    }
}
