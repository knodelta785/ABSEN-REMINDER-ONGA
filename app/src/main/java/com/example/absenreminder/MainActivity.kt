package com.example.absenreminder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate

class MainActivity : Activity() {
    private lateinit var status: TextView
    private val boxes = mutableMapOf<Int, CheckBox>()
    private val names = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "PENGINGAT ABSEN ONGA"
        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        status = TextView(this).apply { textSize = 18f; setPadding(0, 0, 0, pad) }
        root.addView(status)

        fun button(label: String, onClick: () -> Unit) = Button(this).apply {
            setText(label)
            setOnClickListener { onClick() }
        }
        root.addView(button("Sudah absen masuk") { Reminder.confirm(this, Type.MASUK); render() })
        root.addView(button("Sudah absen pulang") { Reminder.confirm(this, Type.PULANG); render() })

        root.addView(TextView(this).apply {
            text = "Hari wajib absen (centang = ada pengingat):"
            textSize = 16f
            setPadding(0, pad, 0, 0)
        })
        val active = Reminder.daysOf(this)
        names.forEachIndexed { i, name ->
            val day = i + 1   // 1 = Senin ... 7 = Minggu
            val cb = CheckBox(this).apply {
                setText(name)
                isChecked = day in active
                setOnCheckedChangeListener { _, _ ->
                    Reminder.setDays(this@MainActivity, boxes.filter { it.value.isChecked }.keys)
                    render()
                }
            }
            boxes[day] = cb
            root.addView(cb)
        }

        root.addView(button("Pengaturan baterai (agar alarm tidak dimatikan)") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })
        setContentView(android.widget.ScrollView(this).apply { addView(root) })

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        Reminder.refresh(this)
        render()
    }

    private fun render() {
        val work = Reminder.isWorkday(this, LocalDate.now())
        fun s(t: Type) = if (Reminder.isDone(this, t)) "✅ sudah" else "❌ belum"
        status.text = if (!work) "Hari ini tidak diaktifkan, tidak ada pengingat."
        else "Hari ini\nAbsen masuk: ${s(Type.MASUK)}\nAbsen pulang: ${s(Type.PULANG)}"
    }
}
