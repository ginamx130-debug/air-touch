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
        color = 0xF20A84FF.toInt()
    }
    private val hudBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x59000000
    }
    private val hudText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9.5f * resources.displayMetrics.scaledDensity
        color = 0xB3FFFFFF.toInt()
        isFakeBoldText = true
    }
    private val rect = RectF()
    private var shownAt = 0L
    private var hidden = false

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

    /** 正常状态 10 秒后自动淡出；错误信息常驻 */
    fun setHud(s: String) {
        val sticky = s.contains("失败") || s.contains("未开") || s.contains("!")
        if (!sticky) {
            if (hidden) return
            if (shownAt == 0L) shownAt = android.os.SystemClock.uptimeMillis()
            if (android.os.SystemClock.uptimeMillis() - shownAt > 10_000) {
                hidden = true; hud = ""; postInvalidate(); return
            }
        } else hidden = false
        if (hud != s) { hud = s; postInvalidate() }
    }
}
