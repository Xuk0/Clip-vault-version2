package com.clipvault.app

import android.animation.ValueAnimator
import android.app.*
import android.content.*
import android.database.ContentObserver
import android.graphics.PixelFormat
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.provider.Settings
import android.view.*
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.clipvault.app.databinding.FloatingBubbleBinding
import com.clipvault.app.databinding.FloatingPanelBinding

class ClipVaultService : Service() {

    // ── WindowManager ──────────────────────────────────────────────────────
    private lateinit var wm: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null
    private var bubbleBinding: FloatingBubbleBinding? = null
    private var panelBinding: FloatingPanelBinding? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    // ── Clipboard ──────────────────────────────────────────────────────────
    private lateinit var clipMgr: ClipboardManager
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { onClipChanged() }

    // ── Screenshot observer ────────────────────────────────────────────────
    private var screenshotObserver: ScreenshotObserver? = null
    private var lastScreenshotPath = ""
    private val mainHandler = Handler(Looper.getMainLooper())

    // ── Broadcast receiver (from main activity) ────────────────────────────
    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) { refreshPanelData() }
    }

    companion object {
        const val CHANNEL_ID     = "clipvault_service"
        const val NOTIF_ID       = 1001
        const val ACTION_REFRESH = "com.clipvault.REFRESH"

        fun start(ctx: Context) =
            ContextCompat.startForegroundService(ctx, Intent(ctx, ClipVaultService::class.java))

        fun stop(ctx: Context) =
            ctx.stopService(Intent(ctx, ClipVaultService::class.java))
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        wm     = getSystemService(WINDOW_SERVICE) as WindowManager
        clipMgr = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Monitoring clipboard & screenshots"))

        clipMgr.addPrimaryClipChangedListener(clipListener)
        startScreenshotObserver()

        registerReceiver(refreshReceiver, IntentFilter(ACTION_REFRESH), RECEIVER_NOT_EXPORTED)

        if (Settings.canDrawOverlays(this)) showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        clipMgr.removePrimaryClipChangedListener(clipListener)
        screenshotObserver?.let { contentResolver.unregisterContentObserver(it) }
        try { unregisterReceiver(refreshReceiver) } catch (_: Exception) {}
        removeBubble()
        removePanel(animate = false)
    }

    override fun onBind(intent: Intent?) = null

    // ── Clipboard monitoring ───────────────────────────────────────────────

    private fun onClipChanged() {
        mainHandler.postDelayed({
            try {
                val clip = clipMgr.primaryClip ?: return@postDelayed
                if (clip.itemCount == 0) return@postDelayed
                val text = clip.getItemAt(0).coerceToText(this).toString().trim()
                if (text.isBlank()) return@postDelayed

                val entry = ClipEntry(type = detectType(text), content = text)
                val added = ClipStorage.addEntry(this, entry)
                if (added) {
                    updateNotification("📋 Saved: ${text.take(40)}")
                    pulseBubble()
                    sendBroadcast(Intent(MainActivity.ACTION_DATA_CHANGED).setPackage(packageName))
                }
            } catch (_: Exception) {}
        }, 200)
    }

    // ── Screenshot detection ───────────────────────────────────────────────

    private fun startScreenshotObserver() {
        screenshotObserver = ScreenshotObserver(mainHandler, this) { path ->
            if (path.isNotBlank() && path != lastScreenshotPath) {
                lastScreenshotPath = path
                val entry = ClipEntry(type = ClipType.SCREENSHOT, content = path, preview = path)
                val added = ClipStorage.addEntry(this, entry)
                if (added) {
                    updateNotification("📸 Screenshot saved")
                    pulseBubble()
                    sendBroadcast(Intent(MainActivity.ACTION_DATA_CHANGED).setPackage(packageName))
                }
            }
        }
        val uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        contentResolver.registerContentObserver(uri, true, screenshotObserver!!)
    }

    // ── Floating Bubble ────────────────────────────────────────────────────

    private fun showBubble() {
        if (bubbleView != null) return
        bubbleBinding = FloatingBubbleBinding.inflate(LayoutInflater.from(this))
        val view = bubbleBinding!!.root
        bubbleView = view

        val dm = resources.displayMetrics
        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dm.widthPixels - dpToPx(68)
            y = dm.heightPixels / 3
        }

        wm.addView(view, bubbleParams)
        setupBubbleDrag()
        view.setOnClickListener { togglePanel() }

        // Entrance animation
        view.scaleX = 0f; view.scaleY = 0f
        view.animate().scaleX(1f).scaleY(1f)
            .setDuration(300).setInterpolator(OvershootInterpolator(2f)).start()
    }

    private fun setupBubbleDrag() {
        val params = bubbleParams ?: return
        var startX = 0; var startY = 0
        var startRawX = 0f; var startRawY = 0f
        var moved = false

        bubbleView?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    startRawX = event.rawX; startRawY = event.rawY
                    moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startRawX
                    val dy = event.rawY - startRawY
                    if (!moved && (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8)) moved = true
                    if (moved) {
                        params.x = (startX + dx).toInt()
                        params.y = (startY + dy).toInt()
                        try { wm.updateViewLayout(bubbleView, params) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) { snapBubbleToEdge(); true }
                    else { v.performClick(); false }
                }
                else -> false
            }
        }
    }

    private fun snapBubbleToEdge() {
        val params = bubbleParams ?: return
        val dm = resources.displayMetrics
        val targetX = if (params.x + dpToPx(32) < dm.widthPixels / 2) 0
                      else dm.widthPixels - dpToPx(64)
        ValueAnimator.ofInt(params.x, targetX).apply {
            duration = 260
            interpolator = OvershootInterpolator(1.4f)
            addUpdateListener {
                params.x = it.animatedValue as Int
                try { wm.updateViewLayout(bubbleView, params) } catch (_: Exception) {}
            }
            start()
        }
    }

    private fun pulseBubble() {
        val v = bubbleView ?: return
        v.animate().scaleX(1.35f).scaleY(1.35f).setDuration(140).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
        }.start()
    }

    private fun removeBubble() {
        bubbleView?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
        }
        bubbleView = null; bubbleBinding = null
    }

    // ── Floating Panel ─────────────────────────────────────────────────────

    private fun togglePanel() {
        if (panelView != null) removePanel() else showPanel()
    }

    private fun showPanel() {
        if (panelView != null) return
        panelBinding = FloatingPanelBinding.inflate(LayoutInflater.from(this))
        val view = panelBinding!!.root
        panelView = view

        val dm = resources.displayMetrics
        val panelParams = WindowManager.LayoutParams(
            (dm.widthPixels * 0.90f).toInt(),
            (dm.heightPixels * 0.68f).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        wm.addView(view, panelParams)

        // Entrance animation
        view.alpha = 0f; view.scaleX = 0.88f; view.scaleY = 0.88f
        view.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(230).setInterpolator(OvershootInterpolator(1.1f)).start()

        // Wire up the panel
        val b = panelBinding!!
        val adapter = FloatingClipAdapter(ClipStorage.load(this).toMutableList(), this)
        b.recyclerView.layoutManager = LinearLayoutManager(this)
        b.recyclerView.adapter = adapter

        b.btnClose.setOnClickListener { removePanel() }
        b.btnOpenApp.setOnClickListener {
            removePanel()
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        }
    }

    private fun refreshPanelData() {
        val b = panelBinding ?: return
        (b.recyclerView.adapter as? FloatingClipAdapter)?.updateData(ClipStorage.load(this).toMutableList())
    }

    private fun removePanel(animate: Boolean = true) {
        val v = panelView ?: return
        if (animate) {
            v.animate().alpha(0f).scaleX(0.88f).scaleY(0.88f).setDuration(190).withEndAction {
                try { wm.removeView(v) } catch (_: Exception) {}
                panelView = null; panelBinding = null
            }.start()
        } else {
            try { wm.removeView(v) } catch (_: Exception) {}
            panelView = null; panelBinding = null
        }
    }

    // ── Notifications ──────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val ch = NotificationChannel(
            CHANNEL_ID, "ClipVault Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Monitors clipboard in background"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ClipVault")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()
}

// ── Screenshot ContentObserver ─────────────────────────────────────────────

class ScreenshotObserver(
    handler: Handler,
    private val context: Context,
    private val onScreenshot: (String) -> Unit
) : ContentObserver(handler) {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        uri ?: return
        mainHandler.postDelayed({ queryForScreenshot(uri) }, 700)
    }

    private fun queryForScreenshot(uri: Uri) {
        try {
            val proj = arrayOf(
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED
            )
            context.contentResolver.query(uri, proj, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val pathIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                    val nameIdx = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    val path = if (pathIdx >= 0) cursor.getString(pathIdx) ?: "" else ""
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                    val lName = name.lowercase()
                    val lPath = path.lowercase()
                    if (lPath.contains("screenshot") || lName.contains("screenshot") ||
                        lName.startsWith("scr_") || lName.startsWith("screen_")) {
                        if (path.isNotBlank()) onScreenshot(path)
                    }
                }
            }
        } catch (_: Exception) {}
    }
}
