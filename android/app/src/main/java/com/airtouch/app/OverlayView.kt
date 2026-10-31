package com.airtouch.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** 系统悬浮窗：指尖光标 + 捏合状态环（不可触摸，纯提示） */
@SuppressLint("ViewConstructor")
class OverlayView(context: Context) : View(context) {

    private var x = -100f
    private var y = -100f
    private var pinched = false
    private var visible = false

    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        setShadowLayer(16f, 0f, 0f, 0x996FA8FF.toInt())
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = 0x887AA8FF.toInt()
    }

    override fun onDraw(c: Canvas) {
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
}
