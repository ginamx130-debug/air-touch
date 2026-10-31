package com.airtouch.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.AlphaAnimation
import android.view.animation.TranslateAnimation
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker

/** 游戏式启动流：Splash 动画 → 加载画面（真实初始化引擎）→ 开始 → 权限引导 → 启动服务 */
class MainActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout

    // screens
    private lateinit var splashView: LinearLayout
    private lateinit var loadView: LinearLayout
    private lateinit var permView: ScrollView

    // loader widgets
    private var bar: ProgressBar? = null
    private var pct: TextView? = null
    private var loadStatus: TextView? = null
    private var startBtn: Button? = null

    // perm widgets
    private lateinit var rowCam: LinearLayout
    private lateinit var rowOvl: LinearLayout
    private lateinit var rowA11y: LinearLayout
    private lateinit var txtCam: TextView
    private lateinit var txtOvl: TextView
    private lateinit var txtA11y: TextView
    private lateinit var btnLaunch: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        immersive()
        root = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF05060A.toInt(), 0xFF0E1022.toInt()))
        }
        buildSplash(); buildLoader(); buildPerms()
        setContentView(root)
        enterSplash()
    }

    override fun onResume() { super.onResume(); immersive(); refreshPerms() }
    override fun onRequestPermissionsResult(
        code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == 1) refreshPerms()
    }

    private fun immersive() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** 苹果风渐变胶囊按钮背景 */
    private fun pill(): GradientDrawable = GradientDrawable().apply {
        orientation = GradientDrawable.Orientation.TL_BR
        setColors(intArrayOf(0xFF5B7CFF.toInt(), 0xFF8A5BFF.toInt()))
        cornerRadius = dp(27).toFloat()
    }

    /* ================= SPLASH ================= */
    private fun buildSplash() {
        splashView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.TRANSPARENT)
        }
        // 呼吸辉光背景
        val glow = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColors(intArrayOf(0x663D6BFF, 0x003D6BFF))
            }
        }
        root.addView(glow, FrameLayout.LayoutParams(dp(420), dp(420)).apply { gravity = Gravity.CENTER })
        listOf("scaleX", "scaleY").forEach {
            val a = android.animation.ObjectAnimator.ofFloat(glow, it, 1f, 1.35f)
            a.duration = 1600
            a.repeatCount = android.animation.ValueAnimator.INFINITE
            a.repeatMode = android.animation.ValueAnimator.REVERSE
            a.start()
        }
        val ga = android.animation.ObjectAnimator.ofFloat(glow, "alpha", 0.55f, 1f)
        ga.duration = 1600
        ga.repeatCount = android.animation.ValueAnimator.INFINITE
        ga.repeatMode = android.animation.ValueAnimator.REVERSE
        ga.start()
        // Logo
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher)
            clipToOutline = true
            background = GradientDrawable().apply { cornerRadius = dp(22).toFloat() }
        }
        splashView.addView(logo, LinearLayout.LayoutParams(dp(96), dp(96)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        val title = TextView(this).apply {
            text = "AirTouch"
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setShadowLayer(24f, 0f, 0f, 0xFF3D6BFF.toInt())
        }
        splashView.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(18)
        })
        val tag = TextView(this).apply {
            text = "悬空触控 · VISION PRO ON YOUR PHONE"
            textSize = 12f
            setTextColor(0xFF8890A8.toInt())
            letterSpacing = 0.2f
            gravity = Gravity.CENTER
        }
        splashView.addView(tag, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        val studio = TextView(this).apply {
            text = "Vibe Coding Studio"
            textSize = 10f
            setTextColor(0xFF4A5068.toInt())
            gravity = Gravity.CENTER
        }
        splashView.addView(studio, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(60) })

        root.addView(splashView, FrameLayout.LayoutParams(-1, -1))
        // 动画：logo 弹入 + 标题上浮
        logo.scaleX = 0.5f; logo.scaleY = 0.5f; logo.alpha = 0f
        logo.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(700)
            .setInterpolator(AccelerateDecelerateInterpolator()).start()
        title.alpha = 0f; title.translationY = dp(24).toFloat()
        title.animate().alpha(1f).translationY(0f).setStartDelay(300).setDuration(600).start()
        tag.alpha = 0f; tag.animate().alpha(1f).setStartDelay(700).setDuration(500).start()
        studio.alpha = 0f; studio.animate().alpha(1f).setStartDelay(1100).setDuration(500).start()
    }

    /* ================= LOADER ================= */
    private fun buildLoader() {
        loadView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.TRANSPARENT)
            visibility = View.GONE
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher)
            clipToOutline = true
            background = GradientDrawable().apply { cornerRadius = dp(17).toFloat() }
        }
        loadView.addView(logo, LinearLayout.LayoutParams(dp(72), dp(72)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        val t = TextView(this).apply {
            text = "正在加载 AirTouch"
            textSize = 17f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        }
        loadView.addView(t, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(22); gravity = Gravity.CENTER_HORIZONTAL })
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 0
            val track = GradientDrawable().apply { cornerRadius = dp(3).toFloat(); setColor(0x24FFFFFF.toInt()) }
            val fill = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xFF5B7CFF.toInt(), 0xFF9B6BFF.toInt())
            ).apply { cornerRadius = dp(3).toFloat() }
            progressDrawable = android.graphics.drawable.LayerDrawable(
                arrayOf(track, android.graphics.drawable.ClipDrawable(
                    fill, Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL))
            ).apply { setId(0, android.R.id.background); setId(1, android.R.id.progress) }
        }
        loadView.addView(bar!!, LinearLayout.LayoutParams(dp(240), dp(8)).apply { topMargin = dp(28); gravity = Gravity.CENTER_HORIZONTAL })
        pct = TextView(this).apply {
            text = "0%"; textSize = 44f
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
        }
        loadView.addView(pct!!, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10); gravity = Gravity.CENTER_HORIZONTAL })
        loadStatus = TextView(this).apply {
            text = "初始化…"; textSize = 12.5f; setTextColor(0xFF8890A8.toInt()); gravity = Gravity.CENTER
        }
        loadView.addView(loadStatus!!, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8); gravity = Gravity.CENTER_HORIZONTAL })
        startBtn = Button(this).apply {
            text = "开  始"
            textSize = 18f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.WHITE)
            background = pill()
            elevation = dp(10).toFloat()
            stateListAnimator = null
            setPadding(dp(52), dp(15), dp(52), dp(15))
            visibility = View.GONE; alpha = 0f
            setOnClickListener { toPerms() }
        }
        loadView.addView(startBtn!!, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(34); gravity = Gravity.CENTER_HORIZONTAL })
        root.addView(loadView, FrameLayout.LayoutParams(-1, -1))
    }

    /* ================= PERMISSIONS ================= */
    private fun buildPerms() {
        permView = ScrollView(this).apply { setBackgroundColor(Color.TRANSPARENT); visibility = View.GONE }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(40), dp(22), dp(30))
        }
        val head = TextView(this).apply {
            text = "开机设置"
            textSize = 24f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
        }
        col.addView(head)
        val sub = TextView(this).apply {
            text = "依次点击下面三个选项，系统会自动弹出询问，全部点亮后即可启动"
            textSize = 13f; setTextColor(0xFF8890A8.toInt()); setPadding(0, dp(8), 0, dp(20))
            setLineSpacing(0f, 1.4f)
        }
        col.addView(sub)

        fun card(tap: () -> Unit): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(0xFF15171E.toInt())
                setStroke(1, 0xFF272B3A.toInt())
            }
            setPadding(dp(18), dp(16), dp(18), dp(16))
            setOnClickListener { tap() }
        }
        fun line(parent: LinearLayout, title: String, make: (TextView) -> TextView): TextView {
            val t = TextView(this).apply {
                textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            }
            parent.addView(t)
            return make(t)
        }
        rowCam = card {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                toast("摄像头已授权")
            else if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.CAMERA, Manifest.permission.POST_NOTIFICATIONS), 1)
            else requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
        }
        txtCam = line(rowCam, "摄像头", { it.setTextColor(0xFFB7C4FF.toInt()); it })
        rowCam.addView(TextView(this).apply {
            text = "用于识别手势（点击自动弹出系统询问）"
            textSize = 12f; setTextColor(0xFF6B7390.toInt()); setPadding(0, dp(4), 0, 0)
        })
        col.addView(rowCam, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        rowOvl = card {
            if (!Settings.canDrawOverlays(this@MainActivity))
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            else toast("悬浮窗已授权")
        }
        txtOvl = TextView(this).apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) }
        rowOvl.addView(txtOvl)
        rowOvl.addView(TextView(this).apply {
            text = "用于在屏幕上显示指尖光标（跳转系统开关）"
            textSize = 12f; setTextColor(0xFF6B7390.toInt()); setPadding(0, dp(4), 0, 0)
        })
        col.addView(rowOvl, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        rowA11y = card {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        txtA11y = TextView(this).apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) }
        rowA11y.addView(txtA11y)
        rowA11y.addView(TextView(this).apply {
            text = "用于把捏合变成真实的点击和滑动（跳转系统开关，找到 AirTouch 开启）"
            textSize = 12f; setTextColor(0xFF6B7390.toInt()); setPadding(0, dp(4), 0, 0)
        })
        col.addView(rowA11y, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        btnLaunch = Button(this).apply {
            text = "启 动"
            textSize = 17f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.WHITE)
            background = pill()
            stateListAnimator = null
            setPadding(0, dp(15), 0, dp(15))
            isEnabled = false; alpha = 0.45f
            setOnClickListener { launch() }
        }
        col.addView(btnLaunch, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) })
        col.addView(TextView(this).apply {
            text = "提示：把 AirTouch 的无障碍服务保持开启，即可在任何 App 里悬空操作。\n停止：回到本 App 点「停止」或关闭无障碍服务。"
            textSize = 11f; setTextColor(0xFF4A5068.toInt()); setPadding(0, dp(16), 0, 0)
            setLineSpacing(0f, 1.5f)
        })
        permView.addView(col)
        root.addView(permView, FrameLayout.LayoutParams(-1, -1))
    }

    private fun refreshPerms() {
        if (!::txtCam.isInitialized) return
        val cam = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val ovl = Settings.canDrawOverlays(this)
        val a11y = enabledA11y()
        txtCam.text = "${if (cam) "✔" else "①"}  摄像头" + if (cam) "  已授权" else ""
        txtOvl.text = "${if (ovl) "✔" else "②"}  悬浮窗" + if (ovl) "  已授权" else ""
        txtA11y.text = "${if (a11y) "✔" else "③"}  无障碍服务" + if (a11y) "  已开启" else ""
        txtCam.setTextColor(if (cam) 0xFF7DFFB2.toInt() else Color.WHITE)
        txtOvl.setTextColor(if (ovl) 0xFF7DFFB2.toInt() else Color.WHITE)
        txtA11y.setTextColor(if (a11y) 0xFF7DFFB2.toInt() else Color.WHITE)
        val all = cam && ovl && a11y
        btnLaunch.isEnabled = all
        btnLaunch.alpha = if (all) 1f else 0.45f
    }

    private fun enabledA11y(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return s?.contains("$packageName/", ignoreCase = true) == true
    }

    /* ================= 流程 ================= */
    private fun enterSplash() {
        main.postDelayed({
            splashView.animate().alpha(0f).setDuration(450).withEndAction {
                splashView.visibility = View.GONE
                loadView.visibility = View.VISIBLE
                loadView.alpha = 0f
                loadView.animate().alpha(1f).setDuration(400).start()
                beginLoading()
            }.start()
        }, 2300)
    }

    private fun beginLoading() {
        val started = System.currentTimeMillis()
        // 真实初始化 MediaPipe 引擎（模型已在 APK 内，无需联网）
        Thread {
            var ok = false
            try {
                val opts = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build())
                    .setRunningMode(RunningMode.VIDEO)
                    .setNumHands(2)
                    .build()
                val lm = HandLandmarker.createFromOptions(this, opts)
                lm.close()
                ok = true
            } catch (_: Throwable) { }
            val wait = (2600 - (System.currentTimeMillis() - started)).coerceAtLeast(0)
            main.postDelayed({
                loadStatus?.text = if (ok) "引擎就绪 · 手势识别已加载" else "引擎初始化异常（启动后重试）"
                bar?.progress = 100
                pct?.text = "100%"
                startBtn?.visibility = View.VISIBLE
                startBtn?.animate()?.alpha(1f)?.setDuration(400)?.start()
                pulse(startBtn!!)
            }, wait)
        }.start()
        // 进度条缓动 0→96
        val anim = android.animation.ValueAnimator.ofInt(0, 96).setDuration(2400)
        anim.interpolator = AccelerateDecelerateInterpolator()
        anim.addUpdateListener {
            bar?.progress = it.animatedValue as Int
            pct?.text = "${bar?.progress}%"
            loadStatus?.text = when (bar!!.progress) {
                in 0..40 -> "载入手势识别引擎…"
                in 41..80 -> "初始化神经网络…"
                else -> "准备悬浮光标…"
            }
        }
        anim.start()
    }

    private fun pulse(v: View) {
        val sx = android.animation.ObjectAnimator.ofFloat(v, "scaleX", 1f, 1.05f)
        val sy = android.animation.ObjectAnimator.ofFloat(v, "scaleY", 1f, 1.05f)
        listOf(sx, sy).forEach {
            it.duration = 750; it.repeatCount = android.animation.ValueAnimator.INFINITE
            it.repeatMode = android.animation.ValueAnimator.REVERSE; it.start()
        }
    }

    private fun toPerms() {
        loadView.animate().alpha(0f).setDuration(350).withEndAction {
            loadView.visibility = View.GONE
            permView.visibility = View.VISIBLE
            permView.alpha = 0f
            permView.animate().alpha(1f).setDuration(400).start()
            refreshPerms()
        }.start()
    }

    private fun launch() {
        if (!(btnLaunch.isEnabled)) return
        startForegroundService(Intent(this, GestureService::class.java))
        Toast.makeText(this, "AirTouch 已启动：回桌面，举起手试试", Toast.LENGTH_LONG).show()
        moveTaskToBack(true)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
