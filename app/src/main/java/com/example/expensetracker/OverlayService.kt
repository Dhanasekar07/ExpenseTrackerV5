package com.example.expensetracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast

class OverlayService : Service() {

    private var wm          : WindowManager?   = null
    private var overlayView : View?            = null
    private var db          : ExpenseDbHelper? = null

    private var amount  = 0.0
    private var source  = ""
    private var channel = ""

    private val handler  = Handler(Looper.getMainLooper())
    private val autoHide = Runnable { dismiss(); stopSelf() }

    companion object {
        private const val CH_ID      = "expense_overlay"
        private const val FG_NOTIF   = 1001
        private const val HIDE_AFTER = 20_000L
    }

    override fun onCreate() {
        super.onCreate()
        db = ExpenseDbHelper(this)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        goForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        amount  = intent?.getDoubleExtra("amount",  0.0) ?: 0.0
        source  = intent?.getStringExtra("source")       ?: ""
        channel = intent?.getStringExtra("channel")      ?: "unknown"
        dismiss()
        showOverlay()
        handler.removeCallbacks(autoHide)
        handler.postDelayed(autoHide, HIDE_AFTER)
        return START_STICKY
    }

    override fun onDestroy() { dismiss(); db?.close(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun goForeground() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CH_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_ID, "Expense Overlay",
                    NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) }
            )
        }
        startForeground(FG_NOTIF,
            Notification.Builder(this, CH_ID)
                .setContentTitle("Expense Tracker Active")
                .setContentText("Monitoring payments...")
                .setSmallIcon(android.R.drawable.ic_menu_save)
                .setOngoing(true).build()
        )
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }

        overlayView = (getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater)
            .inflate(R.layout.overlay_layout, null)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 120 }

        try { wm?.addView(overlayView, lp) }
        catch (e: Exception) { e.printStackTrace(); stopSelf(); return }

        overlayView?.run {
            findViewById<Button>(R.id.btnTea)    .setOnClickListener { log("Tea")     }
            findViewById<Button>(R.id.btnTravel) .setOnClickListener { log("Travel")  }
            findViewById<Button>(R.id.btnGrocery).setOnClickListener { log("Grocery") }
            findViewById<Button>(R.id.btnIgnore) .setOnClickListener { dismiss(); stopSelf() }
        }
    }

    private fun log(category: String) {
        db?.insertExpense(category, amount, source, channel)
        val amtStr = if (amount > 0.0) "₹${String.format("%.2f", amount)}" else "(unknown)"
        Toast.makeText(this, "✓ $category logged — $amtStr", Toast.LENGTH_SHORT).show()
        dismiss()
        stopSelf()
    }

    private fun dismiss() {
        handler.removeCallbacks(autoHide)
        overlayView?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        overlayView = null
    }
}
