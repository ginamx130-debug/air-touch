package com.airtouch.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * 系统悬浮窗：指尖光标 + 捏合状态环 + 实时诊断 HUD
 * HUD 常驻显示服务状态（手数/帧率/捏合值/无障碍），故障时显示原因
 */
@SuppressLint("ViewConstructor")
class OverlayView(context: Context) : View(context) {

    private var x = -100f
    private var y = -100f
    private var pinched = false
    private var visible = false
    private var hud = ""

    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        setShadowLayer(16f, 0f, 0f, 0x996FA8FF.toInt())
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = 0x887AA8FF.toInt()
    }
    private val hudBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xE0141826.toInt()
    }
    private val hudText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f * resources.displayMetrics.scaledDensity
        color = Color.WHITE
        isFakeBoldText = true
    }
    private val rect = RectF()

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    override fun onDraw(c: Canvas) {
        // 诊断胶囊（常驻）
        if (hud.isNotEmpty()) {
            val tw = hudText.measureText(hud)
            val pad = 12f * resources.displayMetrics.density / 2f
            val left = 16f
            val top = 64f
            rect.set(left, top, left + tw + pad * 2, top + hudText.textSize + pad * 2)
            c.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, hudBg)
            c.drawText(hud, left + pad, rect.centerY() + hudText.textSize * 0.35f, hudText)
        }
        // 指尖光标
        if (!visible) return
        if (pinched) {
            c.drawCircle(x, y, 36f, ring)
            dot.color = 0xFFBFD4FF.toInt()
        } else {
            dot.color = 0xE6FFFFFF.toInt()
        }
        c.drawCircle(x, y, 10f, dot)
    }

    fun update(x: Float, y: Float, pinched: Boolean, visible: Boolean) {
        this.x = x; this.y = y; this.pinched = pinched; this.visible = visible
        postInvalidate()
    }

    fun setHud(s: String) { hud = s; postInvalidate() }
}
