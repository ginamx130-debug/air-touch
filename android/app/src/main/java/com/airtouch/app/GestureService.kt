package com.airtouch.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.Handler
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Size
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleService
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import kotlin.math.hypot

/**
 * 前台服务：CameraX 前摄 → MediaPipe 手部关键点 → 手势状态机
 * 光标 = 食指指尖（One Euro 滤波）；捏合 = 点击；捏住移动 = 滑动（分块注入）
 */
class GestureService : LifecycleService() {

    companion object {
        const val ACTION_STOP = "com.airtouch.app.STOP"
        private const val CHANNEL = "airtouch"
        private const val PINCH_ON = 0.33f      // 与网页版一致的双阈值滞回
        private const val PINCH_OFF = 0.46f
        private const val DEBOUNCE = 2          // 连续 N 帧确认
        private const val TAP_MAX_MS = 260L
        private const val TAP_MAX_DIST = 18f
        private const val DRAG_CHUNK = 14f      // 每移动 14px 注入一段滑动
    }

    private var wm: WindowManager? = null
    private var overlay: OverlayView? = null
    private var provider: ProcessCameraProvider? = null
    private var landmarker: HandLandmarker? = null
    private var analyzerExecutor: java.util.concurrent.ExecutorService? = null

    // 手势状态机
    private val fx = OneEuro(); private val fy = OneEuro()
    private var ts = 0L
    private var pinched = false
    private var belowCnt = 0; private var aboveCnt = 0
    private var pinchStartMs = 0L
    private var pinchStartX = 0f; private var pinchStartY = 0f
    private var lastX = -100f; private var lastY = -100f
    private var dispX = 0f; private var dispY = 0f
    private var vibratedOnce = false

    private val mainHandler = Handler(mainLooper)

    override fun onCreate() { super.onCreate(); startNotification() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        initOverlay(); initLandmarker(); startCamera()
        return START_STICKY
    }

    override fun onDestroy() {
        try { provider?.unbindAll() } catch (_: Exception) {}
        try { landmarker?.close() } catch (_: Exception) {}
        analyzerExecutor?.shutdown()
        overlay?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        super.onDestroy()
    }

    /* ---------- 悬浮窗 ---------- */
    private fun initOverlay() {
        if (overlay != null || !Settings.canDrawOverlays(this)) return
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        overlay = OverlayView(this)
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        try { wm?.addView(overlay, p) } catch (_: Exception) { overlay = null }
    }

    /* ---------- 通知 ---------- */
    private fun startNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "AirTouch", NotificationManager.IMPORTANCE_LOW))
        val n: Notification = Notification.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 30)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        else startForeground(1, n)
    }

    /* ---------- MediaPipe ---------- */
    private fun initLandmarker() {
        if (landmarker != null) return
        val base = BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build()
        val opts = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.VIDEO)
            .setNumHands(2)
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .build()
        landmarker = HandLandmarker.createFromOptions(this, opts)
        if (analyzerExecutor == null)
            analyzerExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    }

    /* ---------- CameraX ---------- */
    private fun startCamera() {
        if (provider != null) return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                provider = future.get()
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analyzerExecutor!!, ::analyze)
                provider?.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            } catch (_: Exception) {}
        }, java.util.concurrent.Executor { mainHandler.post(it) })
    }

    /* ---------- 每帧分析 ---------- */
    private fun analyze(proxy: androidx.camera.core.ImageProxy) {
        try {
            val bmp = proxy.toBitmap()
            val m = Matrix()
            m.postRotate(proxy.imageInfo.rotationDegrees.toFloat())
            m.postScale(-1f, 1f) // 前摄镜像，符合直觉
            val upright = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, false)
            proxy.close()

            val lm = landmarker ?: return
            ts += 33
            val result = lm.detectForVideo(BitmapImageBuilder(upright).build(), ts)
            process(result.landmarks())
        } catch (_: Exception) {
            try { proxy.close() } catch (_: Exception) {}
        }
    }

    private fun process(hands: List<List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>>) {
        val dm = resources.displayMetrics
        val sw = dm.widthPixels.toFloat(); val sh = dm.heightPixels.toFloat()

        if (hands.isEmpty()) {
            belowCnt = 0; aboveCnt = 0; vibratedOnce = false
            overlay?.update(lastX, lastY, false, false)
            return
        }
        val lm = hands[0]
        val wrist = lm[0]; val mcp = lm[9]
        val palm = hypot(wrist.x() - mcp.x(), wrist.y() - mcp.y()) + 1e-6f
        val pinch = hypot(lm[4].x() - lm[8].x(), lm[4].y() - lm[8].y()) / palm

        val t = ts.toFloat()
        val sx = fx.filter(t, lm[8].x().toDouble()).toFloat() * sw
        val sy = fy.filter(t, lm[8].y().toDouble()).toFloat() * sh
        lastX = sx; lastY = sy

        if (pinch < PINCH_ON) { belowCnt++; aboveCnt = 0 }
        else if (pinch > PINCH_OFF) { aboveCnt++; belowCnt = 0 }
        else { belowCnt = 0; aboveCnt = 0 }

        val want = if (pinched) aboveCnt < DEBOUNCE else belowCnt >= DEBOUNCE
        val a11y = AirTouchAccessibilityService.INSTANCE

        if (want && !pinched) {
            pinched = true
            pinchStartMs = System.currentTimeMillis()
            pinchStartX = sx; pinchStartY = sy
            dispX = sx; dispY = sy
            vibrate(if (vibratedOnce) 8 else 22); vibratedOnce = true
        } else if (!want && pinched) {
            pinched = false
            vibrate(5)
            val dt = System.currentTimeMillis() - pinchStartMs
            val moved = hypot(sx - pinchStartX, sy - pinchStartY)
            if (dt < TAP_MAX_MS && moved < TAP_MAX_DIST) a11y?.tap(sx, sy)
        }

        if (pinched) {
            if (hypot(sx - dispX, sy - dispY) > DRAG_CHUNK) {
                a11y?.swipe(dispX, dispY, sx, sy, 90)
                dispX = sx; dispY = sy
            }
        }
        overlay?.update(sx, sy, pinched, true)
    }

    private fun vibrate(ms: Long) {
        try {
            val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
            v.vibrate(VibrationEffect.createOneShot(ms, 60))
        } catch (_: Exception) {}
    }

    /** One Euro Filter — 静止防抖、快移跟手（同网页版参数） */
    private class OneEuro(
        private val minCutoff: Double = 1.2,
        private val beta: Double = 0.03,
        private val dCutoff: Double = 1.0
    ) {
        private var xPrev = Double.NaN
        private var dxPrev = 0.0
        private var tPrev = Float.NaN
        private fun alpha(cutoff: Double, dt: Double): Double {
            val tau = 1.0 / (2 * Math.PI * cutoff)
            return 1.0 / (1.0 + tau / dt)
        }
        fun filter(t: Float, x: Double): Double {
            if (xPrev.isNaN()) { xPrev = x; tPrev = t; return x }
            val dt = Math.max(1e-3, (t - tPrev).toDouble())
            tPrev = t
            val dx = (x - xPrev) / dt
            val edx = dxPrev + alpha(dCutoff, dt) * (dx - dxPrev)
            dxPrev = edx
            val cutoff = minCutoff + beta * Math.abs(edx)
            val out = xPrev + alpha(cutoff, dt) * (x - xPrev)
            xPrev = out
            return out
        }
    }
}
