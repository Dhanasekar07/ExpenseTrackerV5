package com.example.expensetracker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

data class BarEntry(val label: String, val value: Float, val color: Int)

class BarGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val barPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color    = Color.parseColor("#757575")
        textSize = 32f
        textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.parseColor("#212121")
        textSize  = 28f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val gridPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = Color.parseColor("#E0E0E0")
        strokeWidth = 1f
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.parseColor("#BDBDBD")
        textSize  = 36f
        textAlign = Paint.Align.CENTER
    }

    private var entries = listOf<BarEntry>()

    fun setData(data: List<BarEntry>) {
        entries = data
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        if (entries.isEmpty()) {
            canvas.drawText("No data yet", w / 2, h / 2, emptyPaint)
            return
        }

        val topPad    = 40f
        val bottomPad = 50f
        val sidePad   = 16f
        val graphH    = h - topPad - bottomPad
        val maxVal    = entries.maxOf { it.value }.coerceAtLeast(1f)

        // Draw horizontal grid lines
        for (i in 0..4) {
            val y = topPad + graphH * (1f - i / 4f)
            canvas.drawLine(sidePad, y, w - sidePad, y, gridPaint)
        }

        val barW   = (w - sidePad * 2) / entries.size
        val barGap = barW * 0.25f

        entries.forEachIndexed { i, entry ->
            val barH    = (entry.value / maxVal) * graphH
            val left    = sidePad + i * barW + barGap / 2
            val right   = left + barW - barGap
            val top     = topPad + graphH - barH
            val bottom  = topPad + graphH

            barPaint.color = entry.color
            val rect = RectF(left, top, right, bottom)
            canvas.drawRoundRect(rect, 8f, 8f, barPaint)

            // Value above bar
            if (entry.value > 0) {
                val valText = if (entry.value >= 1000)
                    "₹${(entry.value / 1000).toInt()}k"
                else
                    "₹${entry.value.toInt()}"
                canvas.drawText(valText, left + (barW - barGap) / 2, top - 8f, valuePaint)
            }

            // Label below bar
            canvas.drawText(entry.label, left + (barW - barGap) / 2, h - 8f, textPaint)
        }
    }
}
