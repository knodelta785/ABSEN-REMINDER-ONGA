package com.example.absenreminder

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * === UBAH JAM DI SINI ===
 * MASUK : pengingat muncul jam 08.00 jika belum absen masuk
 * PULANG: jam pulang 17.00 + 30 menit = 17.30
 */
enum class Type(
    val code: Int,
    val hour: Int,
    val minute: Int,
    val title: String,
    val text: String,
    val button: String
) {
    MASUK(1, 8, 0, "Belum absen masuk", "Kamu belum absen masuk hari ini.", "Sudah absen"),
    PULANG(2, 17, 30, "Belum absen pulang", "Sudah lewat 30 menit dari jam pulang, kamu belum absen pulang.", "Sudah absen");

    val other: Type get() = if (this == MASUK) PULANG else MASUK
}

object Reminder {
    /** Selama belum dikonfirmasi, bunyikan ulang pengingat tiap sekian menit. */
    const val REPEAT_MIN = 15L

    private const val CHANNEL = "absen"
    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private fun prefs(c: Context) = c.getSharedPreferences("absen", Context.MODE_PRIVATE)

    /** Hari aktif (1=Senin ... 7=Minggu). Default Senin-Jumat; tanggal merah tidak diubah otomatis. */
    fun daysOf(c: Context): Set<Int> =
        prefs(c).getString("days", "1,2,3,4,5")!!.split(",").mapNotNull { it.toIntOrNull() }.toSet()

    fun setDays(c: Context, days: Set<Int>) {
        prefs(c).edit().putString("days", days.sorted().joinToString(",")).apply()
        refresh(c)
    }

    fun isWorkday(c: Context, d: LocalDate) = d.dayOfWeek.value in daysOf(c)
    private fun baseTime(d: LocalDate, t: Type) =
        d.atTime(t.hour, t.minute).atZone(ZoneId.systemDefault())

    fun isDone(c: Context, t: Type) =
        prefs(c).getString("done_" + t.name, null) == LocalDate.now().toString()

    private fun setDone(c: Context, t: Type) {
        prefs(c).edit().putString("done_" + t.name, LocalDate.now().toString()).apply()
    }

    /** Dipanggil saat alarm berbunyi. */
    fun onFire(c: Context, t: Type) {
        update(c, t, alert = true)
        update(c, t.other, alert = false)
    }

    /** Dipanggil saat tombol "Sudah absen" ditekan (di notifikasi atau di aplikasi). */
    fun confirm(c: Context, t: Type) {
        setDone(c, t)
        update(c, t, alert = false)
    }

    /** Dipanggil saat boot, aplikasi dibuka, atau jam/zona waktu berubah. */
    fun refresh(c: Context) {
        Type.values().forEach { update(c, it, alert = false) }
    }

    private fun update(c: Context, t: Type, alert: Boolean) {
        val now = ZonedDateTime.now()
        val today = now.toLocalDate()
        val due = isWorkday(c, today) && !now.isBefore(baseTime(today, t)) && !isDone(c, t)
        if (due) show(c, t, alert)
        else c.getSystemService(NotificationManager::class.java).cancel(t.code)
        schedule(c, t)
    }

    private fun nextTrigger(c: Context, t: Type): Long {
        val now = ZonedDateTime.now()
        val today = now.toLocalDate()
        // Sudah lewat jam pengingat hari ini dan belum absen -> ulangi beberapa menit lagi
        if (isWorkday(c, today) && !now.isBefore(baseTime(today, t)) && !isDone(c, t)) {
            val rep = now.plusMinutes(REPEAT_MIN)
            if (rep.toLocalDate() == today) return rep.toInstant().toEpochMilli()
        }
        // Selain itu -> jadwal berikutnya di hari kerja terdekat
        var d = today
        while (true) {
            val b = baseTime(d, t)
            if (isWorkday(c, d) && b.isAfter(now)) return b.toInstant().toEpochMilli()
            d = d.plusDays(1)
        }
    }

    private fun schedule(c: Context, t: Type) {
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            c, 10 + t.code,
            Intent(c, AlarmReceiver::class.java).putExtra("type", t.name), FLAGS
        )
        if (daysOf(c).isEmpty()) {   // tidak ada hari aktif -> matikan alarm
            am.cancel(pi)
            return
        }
        val at = nextTrigger(c, t)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun show(c: Context, t: Type, alert: Boolean) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Pengingat absen", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), FLAGS)
        val confirm = PendingIntent.getBroadcast(
            c, 20 + t.code,
            Intent(c, ConfirmReceiver::class.java).putExtra("type", t.name), FLAGS
        )
        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(t.title)
            .setContentText(t.text)
            .setStyle(Notification.BigTextStyle().bigText(t.text))
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, t.button, confirm).build())
            .setOngoing(true)          // tidak bisa digeser, hilang hanya setelah konfirmasi
            .setAutoCancel(false)
            .setOnlyAlertOnce(!alert)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
        nm.notify(t.code, n)
    }
}
