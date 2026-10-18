package com.vphone.client

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.vphone.client.stream.StreamController
import kotlin.math.min

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

        findViewById<Button>(R.id.btn_home).setOnClickListener { controller?.sendButton("home") }
        findViewById<Button>(R.id.btn_power).setOnClickListener { controller?.sendButton("power") }
        findViewById<Button>(R.id.btn_vol_up).setOnClickListener { controller?.sendButton("volume-up") }
        findViewById<Button>(R.id.btn_vol_down).setOnClickListener { controller?.sendButton("volume-down") }
        findViewById<Button>(R.id.btn_settings).setOnClickListener { showSettings() }

        surface.setOnTouchListener { v, event -> touchInjector.handle(controller, event, v.width, v.height) }
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
            controller?.start(surface.holder.surface, ::onVideoSize, ::onStatus)
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
        controller?.start(holder.surface, ::onVideoSize, ::onStatus)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        controller?.stop()
        controller = null
    }

    // ---- Video sizing + status --------------------------------------------

    private fun onVideoSize(videoW: Int, videoH: Int) {
        runOnUiThread { applyAspect(videoW, videoH) }
    }

    private fun onStatus(text: String) {
        runOnUiThread { status.text = text }
    }

    private fun applyAspect(videoW: Int, videoH: Int) {
        if (videoW <= 0 || videoH <= 0) return
        val pw = if (root.width > 0) root.width else resources.displayMetrics.widthPixels
        val ph = if (root.height > 0) root.height else resources.displayMetrics.heightPixels
        val scale = min(pw.toFloat() / videoW, ph.toFloat() / videoH)
        val w = (videoW * scale).toInt().coerceAtLeast(1)
        val h = (videoH * scale).toInt().coerceAtLeast(1)
        surface.layoutParams = FrameLayout.LayoutParams(w, h, Gravity.CENTER)
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
}
