
package com.example.overlayclicker

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.widget.*
import rikka.shizuku.Shizuku

class MainActivity : Activity() {

    private var selected: ResolveInfo? = null
    private lateinit var shizukuStatus: TextView

    private val requestCode = 1001

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { code, result ->
            if (code == requestCode) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Shizuku izni verildi", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Shizuku izni verilmedi", Toast.LENGTH_LONG).show()
                }
                updateShizukuStatus()
            }
        }

    private val binderReceivedListener =
        Shizuku.OnBinderReceivedListener {
            runOnUiThread { updateShizukuStatus() }
        }

    private val binderDeadListener =
        Shizuku.OnBinderDeadListener {
            runOnUiThread { updateShizukuStatus() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)

        val pm = packageManager

        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#07080C"))
            setPadding(32, 60, 32, 24)
        }

        root.addView(TextView(this).apply {
            text = "OVERLAY CLICKER"
            textSize = 23f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 16)
        })

        shizukuStatus = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.LTGRAY)
            setPadding(16, 18, 16, 18)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#171923"))
                cornerRadius = 18f
            }
        }
        root.addView(
            shizukuStatus,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = 12
            }
        )

        fun btn(t: String, red: Boolean, f: () -> Unit) =
            Button(this).apply {
                text = t
                setTextColor(Color.WHITE)
                setBackgroundColor(
                    Color.parseColor(if (red) "#FF2D55" else "#2A0B16")
                )
                setOnClickListener { f() }
            }

        root.addView(btn("Shizuku iznini kontrol et / iste", false) {
            requestShizukuPermission()
        })

        root.addView(btn("Shizuku uygulamasını aç", false) {
            val launch = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (launch != null) {
                startActivity(launch)
            } else {
                Toast.makeText(
                    this,
                    "Shizuku uygulaması bulunamadı",
                    Toast.LENGTH_LONG
                ).show()
            }
        })

        val separator = TextView(this).apply {
            text = "OYUN SEÇ"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(0, 22, 0, 12)
        }
        root.addView(separator)

        val list = ListView(this)

        val adapter = object : BaseAdapter() {
            override fun getCount() = apps.size
            override fun getItem(i: Int) = apps[i]
            override fun getItemId(i: Int) = i.toLong()

            override fun getView(i: Int, v: View?, p: ViewGroup?): View {
                val ri = apps[i]

                val row = LinearLayout(this@MainActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(20, 20, 20, 20)
                    background = GradientDrawable().apply {
                        setColor(
                            if (ri == selected)
                                Color.parseColor("#2A0B16")
                            else Color.TRANSPARENT
                        )
                        setStroke(
                            if (ri == selected) 3 else 0,
                            Color.parseColor("#FF2D55")
                        )
                        cornerRadius = 16f
                    }
                }

                row.addView(
                    ImageView(this@MainActivity).apply {
                        setImageDrawable(ri.loadIcon(pm))
                    },
                    LinearLayout.LayoutParams(100, 100)
                )

                row.addView(TextView(this@MainActivity).apply {
                    text = ri.loadLabel(pm)
                    setTextColor(Color.WHITE)
                    textSize = 16f
                    setPadding(24, 0, 0, 0)
                })

                return row
            }
        }

        list.adapter = adapter
        list.setOnItemClickListener { _, _, i, _ ->
            selected = apps[i]
            adapter.notifyDataSetChanged()
        }

        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(btn("1) Erişilebilirlik iznini aç", false) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(
                this,
                "Overlay Clicker servisini aç",
                Toast.LENGTH_LONG
            ).show()
        })

        root.addView(btn("2) Overlay ile oyunu başlat", true) {
            val svc = ClickService.instance
            val sel = selected

            when {
                svc == null -> Toast.makeText(
                    this,
                    "Önce erişilebilirlik servisini aç",
                    Toast.LENGTH_LONG
                ).show()

                sel == null -> Toast.makeText(
                    this,
                    "Bir oyun seç",
                    Toast.LENGTH_SHORT
                ).show()

                else -> {
                    svc.showOverlay(
                        sel.loadLabel(pm).toString(),
                        sel.activityInfo.packageName
                    )
                    pm.getLaunchIntentForPackage(
                        sel.activityInfo.packageName
                    )?.let { startActivity(it) }
                }
            }
        })

        setContentView(root)
        updateShizukuStatus()
    }

    private fun requestShizukuPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                Toast.makeText(
                    this,
                    "Önce Shizuku'yu başlat",
                    Toast.LENGTH_LONG
                ).show()
                updateShizukuStatus()
                return
            }

            if (Shizuku.checkSelfPermission() ==
                PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(
                    this,
                    "Shizuku izni zaten verilmiş",
                    Toast.LENGTH_SHORT
                ).show()
                updateShizukuStatus()
                return
            }

            if (Shizuku.shouldShowRequestPermissionRationale()) {
                Toast.makeText(
                    this,
                    "Shizuku uygulamasından izin durumunu kontrol et",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            Shizuku.requestPermission(requestCode)

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Shizuku bağlantısı kurulamadı: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun updateShizukuStatus() {
        if (!::shizukuStatus.isInitialized) return

        try {
            if (!Shizuku.pingBinder()) {
                shizukuStatus.text = "Shizuku: BAĞLI DEĞİL"
                return
            }

            val granted = Shizuku.checkSelfPermission() ==
                PackageManager.PERMISSION_GRANTED

            shizukuStatus.text = if (granted) {
                "Shizuku: BAĞLI • İZİN VERİLDİ"
            } else {
                "Shizuku: BAĞLI • İZİN BEKLENİYOR"
            }

        } catch (e: Exception) {
            shizukuStatus.text = "Shizuku: BAĞLANTI KONTROL EDİLEMEDİ"
        }
    }

    override fun onResume() {
        super.onResume()
        updateShizukuStatus()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        super.onDestroy()
    }
}
