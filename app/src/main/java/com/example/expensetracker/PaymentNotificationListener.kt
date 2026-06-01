package com.example.expensetracker

import android.app.Notification
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class PaymentNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "ExpenseTracker"

        private val SMS_APPS = setOf(
            "com.truecaller.android",
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.android.messaging",
            "com.samsung.android.messaging",
            "com.oneplus.mms",
            "com.oneplus.message",
            "com.miui.sms",
            "com.coloros.mms",
            "com.vivo.mms",
            "com.realme.mms",
            "com.asus.message",
            "com.nokia.messaging",
            "com.motorola.messaging",
            "com.textra",
            "com.handcent.nextsms",
            "com.moez.QKSMS",
            "com.klinker.android.evolve_sms",
            "com.dice.truemessenger"
        )

        private val PAYMENT_APPS = setOf(
            "com.google.android.apps.nbu.paisa.user",
            "com.google.android.apps.walletnfcrel",
            "com.phonepe.app",
            "net.one97.paytm",
            "in.org.npci.upiapp",
            "in.amazon.mShop.android.shopping",
            "com.csam.icici.bank.imobile",
            "com.sbi.lotusintouch",
            "com.snapwork.hdfc",
            "com.axis.mobile",
            "com.msf.kbank.mobile",
            "com.fss.indianbankMobile",
            "com.IndianBank.MobileBanking",
            "com.jio.jiopay",
            "com.freecharge.android",
            "com.mobikwik_new"
        )

        private val STRONG_KEYWORDS = listOf(
            "debited", "credited",
            "credit alert", "debit alert",
            "a/c", "ac no", "acct",
            "avl bal", "avbl bal", "available bal",
            "upi ref", "upi txn", "ref no",
            "upi:", "neft", "imps", "rtgs",
            "your account", "your a/c",
            "cyber fraud"
        )

        private val SOFT_KEYWORDS = listOf(
            "paid", "received", "sent", "transferred",
            "payment", "transaction", "purchase",
            "₹", "rs.", "rs ", "inr", "bank", "balance"
        )

        private val IGNORE_PACKAGES = setOf(
            "com.android.systemui",
            "com.whatsapp", "com.whatsapp.w4b",
            "com.facebook.katana", "com.facebook.orca",
            "com.instagram.android", "com.twitter.android",
            "com.snapchat.android", "com.linkedin.android",
            "com.spotify.music", "com.netflix.mediaclient",
            "com.google.android.youtube",
            "com.swiggy.android", "com.zomato.android",
            "com.flipkart.android"
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val pkg = sbn.packageName ?: return
        if (pkg in IGNORE_PACKAGES) return

        val extras  = sbn.notification?.extras ?: return
        val title   = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()    ?: ""
        val text    = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()     ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        val fullOriginal = listOf(title, text, bigText)
            .filter { it.isNotBlank() }.joinToString(" ")
        val fullLow = fullOriginal.lowercase()

        Log.d(TAG, "[$pkg] title='$title' text='$text'")

        val isSmsPkg     = pkg in SMS_APPS
        val isPaymentPkg = pkg in PAYMENT_APPS
        val hasStrong    = STRONG_KEYWORDS.any { fullLow.contains(it) }
        val hasSoft      = SOFT_KEYWORDS.any  { fullLow.contains(it) }

        val shouldTrigger = when {
            isPaymentPkg && (hasStrong || hasSoft) -> true
            isSmsPkg     && (hasStrong || hasSoft) -> true
            hasStrong                              -> true
            else                                   -> false
        }

        if (!shouldTrigger) return

        // Parse from each field separately — take first non-zero result
        val amount = parseAmount(text)
            .takeIf { it > 0.0 }
            ?: parseAmount(bigText).takeIf { it > 0.0 }
            ?: parseAmount(fullOriginal)

        Log.d(TAG, "  PAYMENT! amount=₹$amount pkg=$pkg")

        val dedup = TransactionDeduplicator(this)
        val hash  = dedup.makeHash(amount, pkg, fullOriginal)
        if (dedup.isSeen(hash)) { Log.d(TAG, "  Duplicate — skip"); return }
        dedup.markSeen(hash)

        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra("amount",  amount)
            putExtra("source",  friendlySource(pkg, title))
            putExtra("snippet", text.ifBlank { title })
            putExtra("channel", if (isSmsPkg) "sms_notification" else "notification")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForegroundService(intent)
        else
            startService(intent)

        Log.d(TAG, "  Overlay fired ✓")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = Unit

    private fun friendlySource(pkg: String, title: String): String {
        if (pkg in SMS_APPS && title.isNotBlank()) return title
        return when (pkg) {
            "com.google.android.apps.nbu.paisa.user" -> "GPay"
            "com.phonepe.app"   -> "PhonePe"
            "net.one97.paytm"   -> "Paytm"
            "com.snapwork.hdfc" -> "HDFC Bank"
            "com.sbi.lotusintouch" -> "SBI YONO"
            "com.csam.icici.bank.imobile" -> "ICICI Bank"
            "com.axis.mobile"   -> "Axis Bank"
            "com.fss.indianbankMobile" -> "Indian Bank"
            else -> pkg
        }
    }

    private fun parseAmount(text: String): Double {
        if (text.isBlank()) return 0.0
        val patterns = listOf(
            Regex("""[+]?(?:₹|Rs\.?\s*|INR\s+)([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""([0-9,]+(?:\.[0-9]{1,2})?)\s*/-""")
        )
        for (p in patterns) {
            val m   = p.find(text) ?: continue
            val amt = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
            if (amt > 0.0) return amt
        }
        return 0.0
    }
}
