package com.example.expensetracker

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var db                  : ExpenseDbHelper
    private lateinit var barGraph            : BarGraphView
    private lateinit var tvTotalSpent        : TextView
    private lateinit var tvTotalCount        : TextView
    private lateinit var categoryContainer   : LinearLayout
    private lateinit var transactionContainer: LinearLayout
    private lateinit var tvNoTransactions    : TextView
    private lateinit var tvDate              : TextView

    private val SMS_REQ       = 1001
    private var currentFilter = "today"

    private val CATEGORIES = listOf(
        Triple("Tea",     "#FF6F00", "☕"),
        Triple("Travel",  "#1565C0", "🚗"),
        Triple("Grocery", "#2E7D32", "🛒"),
        Triple("Other",   "#6A1B9A", "📦")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db                   = ExpenseDbHelper(this)
        barGraph             = findViewById(R.id.barGraphView)
        tvTotalSpent         = findViewById(R.id.tvTotalSpent)
        tvTotalCount         = findViewById(R.id.tvTotalCount)
        categoryContainer    = findViewById(R.id.categoryContainer)
        transactionContainer = findViewById(R.id.transactionContainer)
        tvNoTransactions     = findViewById(R.id.tvNoTransactions)
        tvDate               = findViewById(R.id.tvDate)

        tvDate.text = SimpleDateFormat(
            "EEEE, dd MMM yyyy", Locale.getDefault()
        ).format(Date())

        findViewById<Button>(R.id.btnSetup).setOnClickListener {
            showSetupDialog()
        }

        // Filter tabs
        mapOf(
            R.id.btnToday to "today",
            R.id.btnWeek  to "week",
            R.id.btnMonth to "month",
            R.id.btnAll   to "all"
        ).forEach { (id, filter) ->
            findViewById<Button>(id).setOnClickListener { setFilter(filter, it) }
        }

        // Clear all
        findViewById<Button>(R.id.btnClearAll).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear All Data")
                .setMessage("Delete all transactions?")
                .setPositiveButton("Yes, Clear") { _, _ ->
                    db.clearAll()
                    refreshDashboard()
                    Toast.makeText(this, "All cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Test overlay button — fires immediately then moves app to back
        findViewById<Button>(R.id.btnTestOverlay).setOnClickListener {
            fireTestOverlay()
        }

        TransactionDeduplicator(this).cleanup()

        // Show setup on first launch if permissions missing
        if (!notifListenerEnabled() || !Settings.canDrawOverlays(this)) {
            Handler(Looper.getMainLooper()).postDelayed({ showSetupDialog() }, 500)
        }

        refreshDashboard()
    }

    override fun onResume() {
        super.onResume()
        refreshDashboard()
    }

    // -------------------------------------------------------------------------
    // TEST OVERLAY — fires directly, no delay
    // -------------------------------------------------------------------------

    private fun fireTestOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "⚠ Overlay permission not granted!\nTap ⚙ Setup → Grant Overlay Permission",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        Toast.makeText(
            this,
            "Firing overlay... minimise the app now!",
            Toast.LENGTH_SHORT
        ).show()

        // Start the service
        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra("amount",  99.0)
            putExtra("source",  "TEST_PAYMENT")
            putExtra("snippet", "Test ₹99")
            putExtra("channel", "test")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForegroundService(intent)
        else
            startService(intent)

        // Move to background after short delay so overlay is visible
        Handler(Looper.getMainLooper()).postDelayed({
            moveTaskToBack(true)
        }, 500)
    }

    // -------------------------------------------------------------------------
    // SETUP DIALOG POPUP
    // -------------------------------------------------------------------------

    private fun showSetupDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_setup, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        fun updateStatus() {
            val sms     = hasPerm(Manifest.permission.READ_SMS)
            val notif   = notifListenerEnabled()
            val overlay = Settings.canDrawOverlays(this)

            dialogView.findViewById<TextView>(R.id.tvSmsStatus).apply {
                text = "SMS Access: ${tick(sms)}"
                setTextColor(
                    if (sms) Color.parseColor("#2E7D32")
                    else     Color.parseColor("#F44336")
                )
            }
            dialogView.findViewById<TextView>(R.id.tvNotifStatus).apply {
                text = "Notification Access: ${tick(notif)}"
                setTextColor(
                    if (notif) Color.parseColor("#2E7D32")
                    else       Color.parseColor("#F44336")
                )
            }
            dialogView.findViewById<TextView>(R.id.tvOverlayStatus).apply {
                text = "Overlay Permission: ${tick(overlay)}"
                setTextColor(
                    if (overlay) Color.parseColor("#2E7D32")
                    else         Color.parseColor("#F44336")
                )
            }
        }

        updateStatus()

        dialogView.findViewById<Button>(R.id.btnCloseDialog).setOnClickListener {
            dialog.dismiss()
        }

        // SMS permission
        dialogView.findViewById<Button>(R.id.btnDialogSms).setOnClickListener {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_SMS,
                    Manifest.permission.RECEIVE_SMS
                ),
                SMS_REQ
            )
            Handler(Looper.getMainLooper()).postDelayed({ updateStatus() }, 1000)
        }

        // Notification listener settings
        dialogView.findViewById<Button>(R.id.btnDialogNotification).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            Toast.makeText(
                this,
                "Find 'Expense Tracker' and enable it",
                Toast.LENGTH_LONG
            ).show()
        }

        // Overlay permission settings
        dialogView.findViewById<Button>(R.id.btnDialogOverlay).setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(
                this,
                "Enable 'Allow display over other apps'",
                Toast.LENGTH_LONG
            ).show()
        }

        // Test overlay from dialog
        dialogView.findViewById<Button>(R.id.btnDialogTest).setOnClickListener {
            dialog.dismiss()
            fireTestOverlay()
        }

        dialog.show()
    }

    // -------------------------------------------------------------------------
    // DASHBOARD
    // -------------------------------------------------------------------------

    private fun setFilter(filter: String, btn: View) {
        currentFilter = filter
        listOf(R.id.btnToday, R.id.btnWeek, R.id.btnMonth, R.id.btnAll).forEach {
            findViewById<Button>(it).setBackgroundColor(Color.parseColor("#388E3C"))
        }
        (btn as Button).setBackgroundColor(Color.parseColor("#4CAF50"))
        refreshDashboard()
    }

    private fun getFromTs(): Long {
        val cal = Calendar.getInstance()
        return when (currentFilter) {
            "today" -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.timeInMillis
            }
            "week"  -> { cal.add(Calendar.DAY_OF_YEAR, -7); cal.timeInMillis }
            "month" -> { cal.add(Calendar.MONTH, -1);        cal.timeInMillis }
            else    -> 0L
        }
    }

    private fun refreshDashboard() {
        val fromTs    = getFromTs()
        val expenses  = db.getExpenses(fromTs)
        val catTotals = db.getTotalByCategory(fromTs)

        val total = expenses.sumOf { it.amount }
        tvTotalSpent.text = "₹${String.format("%.0f", total)}"
        tvTotalCount.text = expenses.size.toString()

        // Bar graph
        barGraph.setData(CATEGORIES.map { (name, color, _) ->
            BarEntry(
                label = name,
                value = catTotals[name]?.toFloat() ?: 0f,
                color = Color.parseColor(color)
            )
        })

        // Category breakdown
        categoryContainer.removeAllViews()
        CATEGORIES.forEach { (name, color, icon) ->
            val amount = catTotals[name] ?: 0.0
            val count  = expenses.count { it.category == name }
            if (amount > 0 || count > 0) addCategoryRow(name, icon, color, amount, count)
        }

        // Transactions
        transactionContainer.removeAllViews()
        if (expenses.isEmpty()) {
            tvNoTransactions.visibility = View.VISIBLE
        } else {
            tvNoTransactions.visibility = View.GONE
            expenses.take(20).forEach { addTransactionRow(it) }
        }
    }

    private fun addCategoryRow(
        name: String, icon: String, color: String, amount: Double, count: Int
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 12)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val dot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(16, 16).apply {
                setMargins(0, 0, 12, 0)
            }
            setBackgroundColor(Color.parseColor(color))
        }
        val nameView = TextView(this).apply {
            text     = "$icon $name"
            textSize = 14f
            setTextColor(Color.parseColor("#212121"))
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        val countView = TextView(this).apply {
            text     = "$count txns"
            textSize = 12f
            setTextColor(Color.parseColor("#757575"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 16, 0) }
        }
        val amountView = TextView(this).apply {
            text     = "₹${String.format("%.0f", amount)}"
            textSize = 15f
            setTextColor(Color.parseColor(color))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        row.addView(dot)
        row.addView(nameView)
        row.addView(countView)
        row.addView(amountView)
        categoryContainer.addView(row)
        categoryContainer.addView(makeDivider())
    }

    private fun addTransactionRow(expense: Expense) {
        val cat   = CATEGORIES.find { it.first == expense.category }
        val icon  = cat?.third  ?: "📦"
        val color = cat?.second ?: "#6A1B9A"
        val date  = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
            .format(Date(expense.timestamp))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 10)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val iconView = TextView(this).apply {
            text     = icon
            textSize = 20f
            gravity  = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(48, 48).apply {
                setMargins(0, 0, 12, 0)
            }
        }
        val infoLayout = LinearLayout(this).apply {
            orientation  = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        infoLayout.addView(TextView(this).apply {
            text     = expense.category
            textSize = 14f
            setTextColor(Color.parseColor("#212121"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        infoLayout.addView(TextView(this).apply {
            text     = "$date • ${if (expense.channel == "sms") "SMS" else "Notif"}"
            textSize = 11f
            setTextColor(Color.parseColor("#BDBDBD"))
        })
        val amountView = TextView(this).apply {
            text     = if (expense.amount > 0)
                "₹${String.format("%.0f", expense.amount)}" else "₹-"
            textSize = 15f
            setTextColor(Color.parseColor(color))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        row.addView(iconView)
        row.addView(infoLayout)
        row.addView(amountView)
        transactionContainer.addView(row)
        transactionContainer.addView(makeDivider())
    }

    private fun makeDivider() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1
        )
        setBackgroundColor(Color.parseColor("#F5F5F5"))
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    override fun onRequestPermissionsResult(
        req: Int, perms: Array<String>, results: IntArray
    ) {
        super.onRequestPermissionsResult(req, perms, results)
        if (req == SMS_REQ) refreshDashboard()
    }

    private fun hasPerm(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun notifListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        )
        return flat?.contains(packageName) == true
    }

    private fun tick(ok: Boolean) = if (ok) "✓ Granted" else "✗ Not granted"
}
