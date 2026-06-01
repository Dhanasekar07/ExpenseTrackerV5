package com.example.expensetracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsMessage
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ExpenseTracker"

        private val KEYWORDS = listOf(
            "debited", "credited", "paid", "received", "spent", "sent",
            "withdrawn", "deposited", "transferred", "purchase",
            "₹", "rs.", "rs ", "inr", "credit alert", "debit alert"
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        val bundle = intent.extras ?: return
        val pdus   = bundle.get("pdus") as? Array<*> ?: return
        val format = bundle.getString("format")

        for (pdu in pdus) {
            val sms    = SmsMessage.createFromPdu(pdu as ByteArray, format)
            val sender = sms.originatingAddress ?: continue
            val body   = sms.messageBody ?: continue

            Log.d(TAG, "SMS from [$sender]: $body")

            if (KEYWORDS.none { body.lowercase().contains(it) }) continue

            val amount = parseAmount(body)

            // Dedup using SMS content
            val dedup = TransactionDeduplicator(context)
            val hash  = dedup.makeHash(amount, sender, body)
            if (dedup.isSeen(hash)) {
                Log.d(TAG, "SMS duplicate — skipping")
                continue
            }
            dedup.markSeen(hash)

            Log.d(TAG, "SMS payment detected — amount=$amount sender=$sender")

            val svc = Intent(context, OverlayService::class.java).apply {
                putExtra("amount",  amount)
                putExtra("source",  sender)
                putExtra("snippet", body.take(80))
                putExtra("channel", "sms")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(svc)
            else
                context.startService(svc)

            break
        }
    }

    private fun parseAmount(text: String): Double {
        val regex = Regex(
            """[+]?(?:₹|Rs\.?|INR)\s*([0-9,]+(?:\.[0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        val m = regex.find(text) ?: return 0.0
        return m.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0
    }
}
