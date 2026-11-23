package com.example.overlayclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.*

class ClickService : AccessibilityService() {

    companion object { var instance: ClickService? = null }

    private lateinit var wm: WindowManager
    private val h = Handler(Looper.getMainLooper())
    private var bubble: View? = null
    private var panel: View? = null
    private val points = mutableListOf<TextView>()
    private val lps = HashMap<View, WindowManager.LayoutParams>()
    private var cps = 10
    private var running = false
    private var startBtn: Button? = null

    override fun onServiceConnected() {
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { hideOverlay(); instance = null; super.onDestroy() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun lp(w: Int, h: Int, x: Int, y: Int, touch: Boolean = true) =
        WindowManager.LayoutParams(
            w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                (if (touch) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; this.x = x; this.y = y }

    private fun circle(fill: Int, stroke: Int = 0) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL; setColor(fill)
        if (stroke != 0) setStroke(dp(2), stroke)
    }

    // Sürüklenebilir view; kısa dokunuşta onTap çalışır
    private fun makeDraggable(v: View, p: WindowManager.LayoutParams, onTap: (() -> Unit)? = null) {
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = p.x; oy = p.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx; val dy = e.rawY - sy
                    if (Math.abs(dx) + Math.abs(dy) > dp(6)) moved = true
                    p.x = ox + dx.toInt(); p.y = oy + dy.toInt()
                    wm.updateViewLayout(v, p)
                }
                MotionEvent.ACTION_UP -> if (!moved) onTap?.invoke()
            }
            true
        }
    }

    fun showOverlay() {
        if (bubble != null) return
        // Kırmızı baloncuk (ortası beyaz) + "AÇ"
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val dot = FrameLayout(this).apply { background = circle(Color.parseColor("#FF2D55")) }
        dot.addView(View(this).apply { background = circle(Color.WHITE) }, FrameLayout.LayoutParams(dp(10), dp(10), Gravity.CENTER))
        box.addView(dot, LinearLayout.LayoutParams(dp(30), dp(30)))
        box.addView(TextView(this).apply { text = "AÇ"; textSize = 9f; setTextColor(Color.WHITE); gravity = Gravity.CENTER })
        val bp = lp(dp(36), dp(48), dp(4), dp(200))
        wm.addView(box, bp); lps[box] = bp; bubble = box
        makeDraggable(box, bp) { togglePanel() }
    }

    private fun togglePanel() {
        if (panel != null) { wm.removeView(panel); panel = null; return }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply { setColor(Color.parseColor("#F00E1019")); setStroke(dp(1), Color.parseColor("#E8264A")); cornerRadius = dp(10).toFloat() }
        }
        fun label(t: String, size: Float = 13f) = TextView(this).apply { text = t; setTextColor(Color.WHITE); textSize = size }
        fun button(t: String, red: Boolean, onClick: () -> Unit) = Button(this).apply {
            text = t; setTextColor(Color.WHITE); isAllCaps = false
            background = GradientDrawable().apply { setColor(if (red) Color.parseColor("#FF2D55") else Color.parseColor("#2A0B16")); setStroke(dp(1), Color.parseColor("#E8264A")); cornerRadius = dp(6).toFloat() }
            setOnClickListener { onClick() }
        }
        root.addView(label("OVERLAY CLICKER", 14f))
        val cpsText = label("HIZ: $cps CPS", 16f)
        root.addView(cpsText)
        root.addView(SeekBar(this).apply {
            max = 29; progress = cps - 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, v: Int, u: Boolean) { cps = v + 1; cpsText.text = "HIZ: $cps CPS" }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
        val row = LinearLayout(this)
        row.addView(button("+ Nokta", false) { addPoint() }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(button("− Nokta", false) { removePoint() }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(row)
        startBtn = button(if (running) "DURDUR" else "BAŞLAT", true) { setRunning(!running) }
        root.addView(startBtn)
        root.addView(button("Kapat (overlay)", false) { hideOverlay() })
        val pp = lp(dp(260), WindowManager.LayoutParams.WRAP_CONTENT, dp(50), dp(160))
        wm.addView(root, pp); lps[root] = pp; panel = root
        makeDraggable(label(""), pp)
    }

    private fun addPoint() {
        if (points.size >= 10) return
        val n = points.size + 1
        val tv = TextView(this).apply {
            text = "$n"; gravity = Gravity.CENTER; setTextColor(Color.WHITE); textSize = 16f
            background = circle(Color.parseColor("#66FF2D55"), Color.parseColor("#FF2D55"))
        }
        val p = lp(dp(44), dp(44), dp(120 + n * 20), dp(300 + n * 20), !running)
        wm.addView(tv, p); lps[tv] = p; points.add(tv)
        makeDraggable(tv, p)
    }

    private fun removePoint() {
        val v = points.removeLastOrNull() ?: return
        wm.removeView(v); lps.remove(v)
    }

    private fun setRunning(r: Boolean) {
        running = r
        startBtn?.text = if (r) "DURDUR" else "BAŞLAT"
        // Çalışırken noktalar dokunuşu oyuna geçirsin
        points.forEach { v ->
            val p = lps[v]!!
            p.flags = if (r) p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                      else p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            wm.updateViewLayout(v, p)
        }
        h.removeCallbacks(tick)
        if (r) h.post(tick)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            if (points.isNotEmpty()) {
                val b = GestureDescription.Builder()
                points.forEach { v ->
                    val p = lps[v]!!
                    val path = Path().apply { moveTo(p.x + dp(22).toFloat(), p.y + dp(22).toFloat()) }
                    b.addStroke(GestureDescription.StrokeDescription(path, 0, 5))
                }
                dispatchGesture(b.build(), null, null)
            }
            h.postDelayed(this, 1000L / cps)
        }
    }

    fun hideOverlay() {
        running = false; h.removeCallbacks(tick)
        points.forEach { wm.removeView(it) }; points.clear()
        panel?.let { wm.removeView(it) }; panel = null
        bubble?.let { wm.removeView(it) }; bubble = null
        lps.clear()
    }
}
