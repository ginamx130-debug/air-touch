package com.airtouch.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/** 无障碍服务：全局手势注入的唯一合法通道（点击 / 滑动） */
class AirTouchAccessibilityService : AccessibilityService() {

    companion object {
        @JvmStatic var INSTANCE: AirTouchAccessibilityService? = null
            private set
    }

    private val queue = ArrayDeque<GestureDescription>()
    private var busy = false

    override fun onServiceConnected() { super.onServiceConnected(); INSTANCE = this }
    override fun onUnbind(intent: Intent?): Boolean { INSTANCE = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    val ready: Boolean get() = INSTANCE != null

    fun enqueue(g: GestureDescription) { synchronized(queue) { queue.addLast(g) }; pump() }

    private fun pump() {
        val g = synchronized(queue) { if (busy || queue.isEmpty()) null else queue.pollFirst() } ?: return
        busy = true
        dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(gd: GestureDescription?) { busy = false; pump() }
            override fun onCancelled(gd: GestureDescription?) { busy = false; pump() }
        }, null)
    }

    fun tap(x: Float, y: Float) = enqueue(build(60) {
        moveTo(x, y); lineTo(x + 0.1f, y + 0.1f)
    })

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long) = enqueue(build(ms) {
        moveTo(x1, y1); lineTo(x2, y2)
    })

    private inline fun build(ms: Long, path: Path.() -> Unit): GestureDescription {
        val p = Path().apply(path)
        return GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, ms)).build()
    }
}
