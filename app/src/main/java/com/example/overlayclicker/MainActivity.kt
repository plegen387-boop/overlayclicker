package com.example.overlayclicker

import android.app.Activity
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.widget.*

class MainActivity : Activity() {

    private var selected: ResolveInfo? = null

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val pm = packageManager
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#07080C")); setPadding(32, 80, 32, 32)
        }
        root.addView(TextView(this).apply { text = "OYUN SEÇ"; textSize = 22f; setTextColor(Color.WHITE) })

        val list = ListView(this)
        val adapter = object : BaseAdapter() {
            override fun getCount() = apps.size
            override fun getItem(i: Int) = apps[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, v: View?, p: ViewGroup?): View {
                val ri = apps[i]
                val row = LinearLayout(this@MainActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL; setPadding(20, 20, 20, 20)
                    background = GradientDrawable().apply {
                        setColor(if (ri == selected) Color.parseColor("#2A0B16") else Color.TRANSPARENT)
                        setStroke(if (ri == selected) 3 else 0, Color.parseColor("#FF2D55"))
                    }
                }
                row.addView(ImageView(this@MainActivity).apply { setImageDrawable(ri.loadIcon(pm)) }, LinearLayout.LayoutParams(120, 120))
                row.addView(TextView(this@MainActivity).apply {
                    text = ri.loadLabel(pm); setTextColor(Color.WHITE); textSize = 16f; setPadding(30, 0, 0, 0)
                })
                return row
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, i, _ -> selected = apps[i]; adapter.notifyDataSetChanged() }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))

        fun btn(t: String, red: Boolean, f: () -> Unit) = Button(this).apply {
            text = t; setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor(if (red) "#FF2D55" else "#2A0B16"))
            setOnClickListener { f() }
        }
        root.addView(btn("1) Erişilebilirlik iznini aç", false) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Overlay Clicker servisini aç", Toast.LENGTH_LONG).show()
        })
        root.addView(btn("2) Overlay ile oyunu başlat", true) {
            val svc = ClickService.instance
            val sel = selected
            when {
                svc == null -> Toast.makeText(this, "Önce erişilebilirlik servisini aç", Toast.LENGTH_LONG).show()
                sel == null -> Toast.makeText(this, "Bir oyun seç", Toast.LENGTH_SHORT).show()
                else -> {
                    svc.showOverlay()
                    pm.getLaunchIntentForPackage(sel.activityInfo.packageName)?.let { startActivity(it) }
                }
            }
        })
        setContentView(root)
    }
}
