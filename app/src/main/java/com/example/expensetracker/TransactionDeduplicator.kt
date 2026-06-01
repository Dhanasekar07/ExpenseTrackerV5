package com.example.expensetracker

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

class TransactionDeduplicator(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("expense_dedup", Context.MODE_PRIVATE)

    companion object {
        private const val WINDOW_MS = 2_000L
        private const val PREFIX    = "h_"
    }

    fun makeHash(amount: Double, source: String, text: String): String {
        val bucket = System.currentTimeMillis() / WINDOW_MS
        val raw    = "$amount|$source|$text|$bucket"
        val bytes  = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun isSeen(hash: String): Boolean {
        val t = prefs.getLong(PREFIX + hash, -1L)
        return t != -1L && (System.currentTimeMillis() - t) < WINDOW_MS
    }

    fun markSeen(hash: String) {
        prefs.edit().putLong(PREFIX + hash, System.currentTimeMillis()).apply()
    }

    fun cleanup() {
        val now  = System.currentTimeMillis()
        val edit = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith(PREFIX) }
            .forEach { key ->
                if (now - prefs.getLong(key, 0L) > 60_000L)
                    edit.remove(key)
            }
        edit.apply()
    }
}
