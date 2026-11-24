package com.example.overlayclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.*

class Trig(var x: Int, var y: Int, var size: Int, var tx: Int, var ty: Int, var mode: Int = 0, var cps: Int = 14) {
    var view: TextView? = null
    var active = false
    var next = 0L
    var downAt = 0L
    var lastUp = 0L
    var hs: GestureDescription.StrokeDescription? = null
    var longRun: Runnable? = null
}

class ClickService : AccessibilityService() {

    companion object { var instance: ClickService? = null }

    private val RED = Color.parseColor("#FF2D55")
    private val CARD = Color.parseColor("#2A0B16")
    private val PANEL = Color.parseColor("#F00E1019")
    private val MODES = arrayOf("SERİ", "BASILI", "TEK", "ÇİFT", "BIRAKINCA")
    private val DESC = arrayOf(
        "Tetiği tuttuğun sürece seri dokunur.",
        "Tetiği basılı tuttuğun sürece hızlıca dokunur. Bırakınca durur.",
        "Dokun: seri dokunma açılır. Tekrar dokun: kapanır.",
        "Çift dokun: açılır. Tekrar çift dokun: kapanır.",
        "Parmağını çekince hedefe bir kez dokunur.")

    private lateinit var wm: WindowManager
    private val h = Handler(Looper.getMainLooper())
    private val trigs = mutableListOf<Trig>()
    private val wins = mutableListOf<View>()          // tetik, hedef, tutamaç, çizgi pencereleri
    private var bubble: LinearLayout? = null
    private var bubbleLp: WindowManager.LayoutParams? = null
    private var bubbleLabel: TextView? = null
    private var menu: View? = null
    private var bar: View? = null
    private var sheet: View? = null
    private var lines: View? = null
    private var pkg = ""
    private var gname = ""
    private var armed = false
    private var editing = false
    private var busy = false
    private var busyAt = 0L

    override fun onServiceConnected() { instance = this; wm = getSystemService(WINDOW_SERVICE) as WindowManager }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() { stopAll() }
    override fun onDestroy() { hideOverlay(); instance = null; super.onDestroy() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun lp(w: Int, hh: Int, x: Int, y: Int, touch: Boolean = true) =
        WindowManager.LayoutParams(w, hh, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                (if (touch) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; this.x = x; this.y = y }

    private fun shape(fill: Int, stroke: Int, dashed: Boolean = false, oval: Boolean = true) = GradientDrawable().apply {
        if (oval) shape = GradientDrawable.OVAL else cornerRadius = dp(6).toFloat()
        setColor(fill)
        setStroke(dp(2), stroke, if (dashed) dp(5).toFloat() else 0f, if (dashed) dp(4).toFloat() else 0f)
    }

    private fun addW(v: View, p: WindowManager.LayoutParams) { wm.addView(v, p); wins.add(v) }
    private fun rm(v: View?) { if (v != null) try { wm.removeView(v) } catch (e: Exception) {} }

    // ---------- UI yardımcıları ----------
    private fun label(t: String, size: Float = 13f, bold: Boolean = false) = TextView(this).apply {
        text = t; setTextColor(Color.WHITE); textSize = size; if (bold) setTypeface(null, Typeface.BOLD)
    }
    private fun btn(t: String, red: Boolean, f: () -> Unit) = Button(this).apply {
        text = t; setTextColor(Color.WHITE); isAllCaps = true; textSize = 12f; setTypeface(null, Typeface.BOLD)
        background = shape(if (red) RED else CARD, RED, false, false)
        setOnClickListener { f() }
    }
    private fun box() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12))
        background = shape(PANEL, Color.parseColor("#E8264A"), false, false)
    }

    // ---------- sürükleme ----------
    private fun drag(v: View, p: WindowManager.LayoutParams, onMove: (Int, Int) -> Unit, onTap: (() -> Unit)? = null) {
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = p.x; oy = p.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - sx).toInt(); val dy = (e.rawY - sy).toInt()
                    if (Math.abs(dx) + Math.abs(dy) > dp(6)) moved = true
                    if (moved) onMove(ox + dx, oy + dy)
                }
                MotionEvent.ACTION_UP -> if (!moved) onTap?.invoke()
            }
            true
        }
    }

    // ---------- başlat / kapat ----------
    fun showOverlay(name: String, packageName: String) {
        if (bubble != null) return
        gname = name; pkg = packageName
        load(); armed = trigs.isNotEmpty(); editing = false
        val b = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val dot = FrameLayout(this).apply { background = shape(RED, Color.WHITE) }
        dot.addView(View(this).apply { background = shape(Color.WHITE, Color.WHITE) }, FrameLayout.LayoutParams(dp(10), dp(10), Gravity.CENTER))
        b.addView(dot, LinearLayout.LayoutParams(dp(30), dp(30)))
        bubbleLabel = label("AÇ", 9f, true).apply { gravity = Gravity.CENTER; setShadowLayer(3f, 0f, 1f, Color.BLACK) }
        b.addView(bubbleLabel)
        val p = lp(dp(40), dp(50), dp(2), dp(150))
        wm.addView(b, p); bubble = b; bubbleLp = p
        drag(b, p, { x, y -> p.x = x; p.y = y; wm.updateViewLayout(b, p) }) {
            when {
                trigs.any { it.active } -> stopAll()      // çalışırken baloncuk = DUR
                editing -> {}
                else -> toggleMenu()
            }
        }
        rebuild()
    }

    fun hideOverlay() {
        stopAll(); clearWins(); rm(menu); rm(bar); rm(sheet); rm(bubble)
        menu = null; bar = null; sheet = null; bubble = null; trigs.clear()
    }

    private fun updateBubble() {
        val on = trigs.any { it.active }
        bubbleLabel?.text = if (on) "DUR" else if (armed) "AUTO" else if (editing) "DÜZ." else "AÇ"
        bubbleLabel?.setTextColor(if (on || armed) RED else Color.WHITE)
    }

    // ---------- kayıt ----------
    private fun save() {
        getSharedPreferences("km", 0).edit().putString(pkg,
            trigs.joinToString(";") { "${it.x},${it.y},${it.size},${it.tx},${it.ty},${it.mode},${it.cps}" }).apply()
    }
    private fun load() {
        trigs.clear()
        (getSharedPreferences("km", 0).getString(pkg, "") ?: "").split(";").filter { it.isNotBlank() }.forEach {
            val a = it.split(",").map { s -> s.toInt() }
            trigs.add(Trig(a[0], a[1], a[2], a[3], a[4], a[5], a[6]))
        }
    }

    // ---------- tetik pencereleri ----------
    private fun clearWins() { wins.forEach { rm(it) }; wins.clear(); lines = null }

    private fun rebuild() {
        stopAll(); clearWins()
        if (editing) {
            val lv = object : View(this) {
                val pt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = dp(1).toFloat(); color = Color.argb(180, 255, 255, 255)
                    pathEffect = DashPathEffect(floatArrayOf(dp(6).toFloat(), dp(5).toFloat()), 0f)
                }
                override fun onDraw(c: Canvas) {
                    trigs.forEach { c.drawLine((it.x + it.size / 2).toFloat(), (it.y + it.size / 2).toFloat(), (it.tx + dp(22)).toFloat(), (it.ty + dp(22)).toFloat(), pt) }
                }
            }
            addW(lv, lp(-1, -1, 0, 0, false)); lines = lv
        }
        trigs.forEachIndexed { i, t -> buildTrig(t, i) }
        updateBubble()
    }

    private fun styleTrig(t: Trig) {
        t.view?.background = when {
            editing -> shape(Color.parseColor("#66FF2D55"), RED)
            t.active -> shape(Color.parseColor("#CCFF2D55"), Color.WHITE)
            else -> shape(Color.parseColor("#26000000"), Color.parseColor("#B3FFFFFF"), true)
        }
    }

    private fun buildTrig(t: Trig, i: Int) {
        val tv = TextView(this).apply {
            gravity = Gravity.CENTER; setTextColor(Color.WHITE); textSize = 10f; setTypeface(null, Typeface.BOLD)
            text = if (editing) "${i + 1}\nTETİK\n${t.cps} CPS" else "${i + 1}"
        }
        t.view = tv; styleTrig(t)
        val p = lp(t.size, t.size, t.x, t.y)
        addW(tv, p)
        if (editing) {
            val hv = View(this).apply { background = shape(Color.WHITE, Color.WHITE) }
            val hp = lp(dp(18), dp(18), t.x + t.size - dp(12), t.y + t.size - dp(12))
            addW(hv, hp)
            val tg = TextView(this).apply {
                text = "+"; gravity = Gravity.CENTER; setTextColor(Color.WHITE); textSize = 24f
                background = shape(Color.parseColor("#66000000"), Color.WHITE)
            }
            val gp = lp(dp(44), dp(44), t.tx, t.ty)
            addW(tg, gp)
            drag(tv, p, { x, y ->
                t.x = x; t.y = y; p.x = x; p.y = y; wm.updateViewLayout(tv, p)
                hp.x = x + t.size - dp(12); hp.y = y + t.size - dp(12); wm.updateViewLayout(hv, hp); lines?.invalidate()
            }) { openSheet(t) }
            drag(hv, hp, { x, y ->
                val s = Math.max(dp(44), Math.max(x - t.x, y - t.y) + dp(12))
                t.size = Math.min(s, dp(180)); p.width = t.size; p.height = t.size; wm.updateViewLayout(tv, p)
                hp.x = t.x + t.size - dp(12); hp.y = t.y + t.size - dp(12); wm.updateViewLayout(hv, hp); lines?.invalidate()
            })
            drag(tg, gp, { x, y -> t.tx = x; t.ty = y; gp.x = x; gp.y = y; wm.updateViewLayout(tg, gp); lines?.invalidate() })
        } else {
            t.longRun = Runnable { if (armed) { setActive(t, !t.active) } }
            tv.setOnTouchListener { v, e ->
                val now = SystemClock.uptimeMillis()
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        t.downAt = now; v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(80).start()
                        if (t.mode == 0 || t.mode == 1) setActive(t, true)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                        h.removeCallbacks(t.longRun!!)
                        if (e.action == MotionEvent.ACTION_CANCEL) { if (t.mode == 0 || t.mode == 1) setActive(t, false) }
                        else when (t.mode) {
                            0, 1 -> setActive(t, false)
                            2 -> if (now - t.downAt < 350) setActive(t, !t.active)
                            3 -> if (now - t.downAt < 350) {
                                if (now - t.lastUp < 400) { setActive(t, !t.active); t.lastUp = 0 } else t.lastUp = now
                            }
                            4 -> tapOnce(t)
                        }
                    }
                }
                true
            }
        }
    }

    // ---------- tıklama motoru ----------
    private fun stopAll() { trigs.forEach { if (it.active) setActive(it, false) } }

    private fun setActive(t: Trig, on: Boolean) {
        if (t.active == on) return
        t.active = on; t.next = 0; styleTrig(t); updateBubble()
        h.removeCallbacks(tick); if (trigs.any { it.active }) h.post(tick)
    }

    private val cb = object : AccessibilityService.GestureResultCallback() {
        override fun onCompleted(g: GestureDescription?) { busy = false }
        override fun onCancelled(g: GestureDescription?) { busy = false }
    }

    private fun stroke(x: Int, y: Int, dur: Long = 8) =
        GestureDescription.StrokeDescription(Path().apply { moveTo(x.toFloat(), y.toFloat()) }, 0, dur)

    private fun onBubble(x: Int, y: Int): Boolean {
        val b = bubbleLp ?: return false
        return x >= b.x && x <= b.x + b.width && y >= b.y && y <= b.y + b.height
    }

    private fun tapOnce(t: Trig) {
        val x = t.tx + dp(22); val y = t.ty + dp(22)
        if (!onBubble(x, y)) dispatchGesture(GestureDescription.Builder().addStroke(stroke(x, y)).build(), null, null)
    }

    private val tick = object : Runnable {
        override fun run() {
            val act = trigs.filter { it.active }
            if (act.isEmpty()) return
            val now = SystemClock.uptimeMillis()
            if (busy && now - busyAt > 150) busy = false
            if (!busy) {
                val b = GestureDescription.Builder(); var n = 0
                for (t in act) if (now >= t.next && n < 9) {
                    val x = t.tx + dp(22); val y = t.ty + dp(22)
                    t.next = now + 1000L / t.cps
                    if (onBubble(x, y)) continue         // kendi baloncuğumuza asla dokunma
                    b.addStroke(stroke(x, y)); n++
                }
                if (n > 0) { busy = true; busyAt = now; if (!dispatchGesture(b.build(), cb, null)) busy = false }
            }
            h.postDelayed(this, 6)
        }
    }

    // BASILI: hedefe basılı tutma (parça parça uzatılır)
    private fun holdStart(t: Trig) {
        val s = GestureDescription.StrokeDescription(Path().apply { moveTo((t.tx + dp(22)).toFloat(), (t.ty + dp(22)).toFloat()) }, 0, 600, true)
        t.hs = s; dispatchHold(t, s)
    }
    private fun dispatchHold(t: Trig, s: GestureDescription.StrokeDescription) {
        dispatchGesture(GestureDescription.Builder().addStroke(s).build(), object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) {
                if (t.active && t.hs != null) {
                    val n = t.hs!!.continueStroke(Path().apply { moveTo((t.tx + dp(22)).toFloat(), (t.ty + dp(22)).toFloat()) }, 0, 600, true)
                    t.hs = n; dispatchHold(t, n)
                }
            }
        }, null)
    }
    private fun holdStop(t: Trig) {
        val s = t.hs ?: return
        t.hs = null
        val e = s.continueStroke(Path().apply { moveTo((t.tx + dp(22)).toFloat(), (t.ty + dp(22)).toFloat()) }, 0, 1, false)
        dispatchGesture(GestureDescription.Builder().addStroke(e).build(), null, null)
    }

    // ---------- menü (yan panel) ----------
    private fun toggleMenu() {
        if (menu != null) { rm(menu); menu = null; return }
        val r = box()
        val hd = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        hd.addView(label(gname, 16f, true), LinearLayout.LayoutParams(0, -2, 1f))
        hd.addView(label("✕", 20f).apply { setOnClickListener { rm(menu); menu = null } })
        r.addView(hd)
        r.addView(btn("KEYMAP · ${trigs.size} tetik, düzenle", true) {
            rm(menu); menu = null; editing = true; armed = false; rebuild(); showBar()
        })
        r.addView(btn("MAKRO · yakında", false) {})
        r.addView(btn("Uygulamayı aç · kitaplık ve ayarlar", false) {
            rm(menu); menu = null
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
        r.addView(btn("EXIT OVERLAY", false) { hideOverlay() })
        val p = lp(dp(290), -2, dp(8), dp(40)); wm.addView(r, p); menu = r
    }

    // ---------- düzenleme çubuğu ----------
    private fun showBar() {
        rm(bar)
        val r = box().apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val tt = label("KEYMAP\n$gname", 11f, true)
        r.addView(tt)
        val cnt = label(" ${trigs.size}/6 ", 11f)
        r.addView(cnt)
        r.addView(btn("+ TETİK", false) {
            if (trigs.size < 6) {
                val i = trigs.size
                trigs.add(Trig(dp(60), dp(80) + i * dp(80), dp(70), dp(180), dp(80) + i * dp(80)))
                rebuild(); cnt.text = " ${trigs.size}/6 "
            }
        })
        r.addView(btn("İPTAL", false) { rm(sheet); sheet = null; load(); editing = false; armed = trigs.isNotEmpty(); rebuild(); rm(bar); bar = null })
        r.addView(btn("✓ KAYDET", true) { rm(sheet); sheet = null; save(); editing = false; armed = trigs.isNotEmpty(); rebuild(); rm(bar); bar = null })
        val sw = resources.displayMetrics.widthPixels; val sh = resources.displayMetrics.heightPixels
        val p = lp(-2, -2, Math.max(dp(8), sw / 2 - dp(200)), sh - dp(90)); wm.addView(r, p); bar = r
    }

    // ---------- tetik ayar paneli ----------
    private fun openSheet(t: Trig) {
        rm(sheet)
        val i = trigs.indexOf(t)
        val r = box()
        r.addView(label("▍Tetik ${i + 1}", 16f, true))
        val row = LinearLayout(this)
        val desc = label(DESC[t.mode], 11f)
        val mb = ArrayList<Button>()
        MODES.forEachIndexed { m, nm ->
            val b = btn(nm, m == t.mode) {
                t.mode = m; desc.text = DESC[m]
                mb.forEachIndexed { k, x -> x.background = shape(if (k == m) RED else CARD, RED, false, false) }
            }
            b.textSize = 8f; b.setPadding(0, 0, 0, 0); mb.add(b)
            row.addView(b, LinearLayout.LayoutParams(0, dp(40), 1f))
        }
        r.addView(row); r.addView(desc)
        r.addView(label("HIZ", 11f))
        val cv = label("${t.cps} CPS", 22f, true).apply { gravity = Gravity.END }
        r.addView(cv)
        val sb = SeekBar(this).apply {
            max = 29; progress = t.cps - 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, v: Int, u: Boolean) { t.cps = v + 1; cv.text = "${t.cps} CPS"; t.view?.text = "${i + 1}\nTETİK\n${t.cps} CPS" }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        val sr = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        sr.addView(btn("−", false) { sb.progress = sb.progress - 1 }, LinearLayout.LayoutParams(dp(44), dp(44)))
        sr.addView(sb, LinearLayout.LayoutParams(0, -2, 1f))
        sr.addView(btn("+", false) { sb.progress = sb.progress + 1 }, LinearLayout.LayoutParams(dp(44), dp(44)))
        r.addView(sr)
        val br = LinearLayout(this)
        br.addView(btn("🗑 SİL", false) { trigs.remove(t); rm(sheet); sheet = null; rebuild() }, LinearLayout.LayoutParams(0, -2, 1f))
        br.addView(btn("TAMAM", true) { rm(sheet); sheet = null }, LinearLayout.LayoutParams(0, -2, 1f))
        r.addView(br)
        val sw = resources.displayMetrics.widthPixels
        val p = lp(dp(300), -2, Math.max(dp(8), sw - dp(310)), dp(30)); wm.addView(r, p); sheet = r
    }
}
