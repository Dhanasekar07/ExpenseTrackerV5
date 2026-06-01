package com.example.expensetracker

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Expense(
    val id       : Long,
    val category : String,
    val amount   : Double,
    val source   : String,
    val channel  : String,
    val timestamp: Long
)

class ExpenseDbHelper(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME    = "expenses.db"
        private const val DB_VERSION = 1
        const val TABLE        = "expenses"
        const val COL_ID       = "id"
        const val COL_CATEGORY = "category"
        const val COL_AMOUNT   = "amount"
        const val COL_SOURCE   = "source"
        const val COL_CHANNEL  = "channel"
        const val COL_TS       = "timestamp"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE (
                $COL_ID       INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_CATEGORY TEXT    NOT NULL,
                $COL_AMOUNT   REAL,
                $COL_SOURCE   TEXT,
                $COL_CHANNEL  TEXT,
                $COL_TS       INTEGER NOT NULL
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    fun insertExpense(
        category: String,
        amount  : Double,
        source  : String,
        channel : String = "unknown"
    ): Long {
        val cv = ContentValues().apply {
            put(COL_CATEGORY, category)
            put(COL_AMOUNT,   amount)
            put(COL_SOURCE,   source)
            put(COL_CHANNEL,  channel)
            put(COL_TS,       System.currentTimeMillis())
        }
        return writableDatabase.insert(TABLE, null, cv)
    }

    fun getExpenses(fromTs: Long = 0L): List<Expense> {
        val list = mutableListOf<Expense>()
        val cursor: Cursor = readableDatabase.query(
            TABLE, null,
            "$COL_TS >= ?", arrayOf(fromTs.toString()),
            null, null, "$COL_TS DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(Expense(
                    id        = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                    category  = it.getString(it.getColumnIndexOrThrow(COL_CATEGORY)),
                    amount    = it.getDouble(it.getColumnIndexOrThrow(COL_AMOUNT)),
                    source    = it.getString(it.getColumnIndexOrThrow(COL_SOURCE)) ?: "",
                    channel   = it.getString(it.getColumnIndexOrThrow(COL_CHANNEL)) ?: "",
                    timestamp = it.getLong(it.getColumnIndexOrThrow(COL_TS))
                ))
            }
        }
        return list
    }

    fun clearAll() {
        writableDatabase.delete(TABLE, null, null)
    }

    fun getTotalByCategory(fromTs: Long = 0L): Map<String, Double> {
        val map = mutableMapOf<String, Double>()
        val cursor = readableDatabase.rawQuery(
            "SELECT $COL_CATEGORY, SUM($COL_AMOUNT) FROM $TABLE WHERE $COL_TS >= ? GROUP BY $COL_CATEGORY",
            arrayOf(fromTs.toString())
        )
        cursor.use {
            while (it.moveToNext()) {
                map[it.getString(0)] = it.getDouble(1)
            }
        }
        return map
    }
}
