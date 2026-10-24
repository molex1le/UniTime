package com.molex.nextpair

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Обновления приложения из публичных релизов github.com/molex1le/UniTime.
 * Раз в день тихо проверяет последний релиз; если версия новее — уведомление и окно в приложении.
 * Установка — системным установщиком (подтверждение одним нажатием).
 */
object AppUpdate {
    private const val LATEST = "https://api.github.com/repos/molex1le/UniTime/releases/latest"
    private const val CHANNEL = "app_update"
    private const val NOTIF_ID = 40
    /** Пометка в описании релиза: окно нельзя закрыть кнопкой «Позже». */
    const val MANDATORY_MARK = "[важно]"

    data class Release(val version: String, val notes: String, val apkUrl: String, val size: Long, val mandatory: Boolean)

    fun current(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull()
            ?.substringBefore("-") ?: "0"

    /** «3.10» новее «3.9»: сравниваем по числам, а не по строке. */
    fun isNewer(candidate: String, installed: String): Boolean {
        val a = candidate.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val b = installed.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun parse(json: String): Release? {
        val o = JSONObject(json)
        val apk = o.optJSONArray("assets")?.let { a ->
            (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk") }
        } ?: return null
        val body = o.optString("body").takeIf { it != "null" }.orEmpty()
        return Release(
            version = o.optString("tag_name").removePrefix("v"),
            notes = body.replace(MANDATORY_MARK, "", ignoreCase = true).trim(),
            apkUrl = apk.getString("browser_download_url"),
            size = apk.optLong("size"),
            mandatory = body.contains(MANDATORY_MARK, ignoreCase = true),
        )
    }

    /** Последний релиз, если он новее установленной версии. Сеть — не из главного потока. */
    fun checkNewer(ctx: Context): Release? {
        val c = URL(LATEST).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 20000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (c.responseCode != 200) return null
            val r = parse(c.inputStream.bufferedReader().readText()) ?: return null
            return r.takeIf { isNewer(it.version, current(ctx)) }
        } finally {
            c.disconnect()
        }
    }

    /** Фоновая проверка: уведомить один раз о каждой новой версии. */
    fun backgroundCheck(ctx: Context) {
        val st = Settings(ctx)
        if (!st.updateCheck) return
        val r = runCatching { checkNewer(ctx) }.getOrNull() ?: return
        if (st.updateNotified == r.version) return
        st.updateNotified = r.version
        notify(ctx, r)
    }

    private fun notify(ctx: Context, r: Release) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Обновления UniTime", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            ctx, 4, Intent(ctx, MainActivity::class.java).putExtra("showUpdate", true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = r.notes.lineSequence().firstOrNull { it.isNotBlank() }?.trimStart('-', '•', ' ') ?: "Нажми, чтобы обновить"
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_pair)
            .setContentTitle("Доступна UniTime ${r.version}")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(r.notes.ifBlank { "Нажми, чтобы обновить" }))
            .setColor(0xFF2B5CE6.toInt())
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { nm.notify(NOTIF_ID, n) }
    }

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(ctx, 6, Intent(ctx, UpdateReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (!Settings(ctx).updateCheck) { am.cancel(pi); return }
        am.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + AlarmManager.INTERVAL_HOUR, AlarmManager.INTERVAL_DAY, pi)
    }

    /** Скачивает APK во внутренний кэш; [progress] — от 0 до 1. Не из главного потока. */
    fun download(ctx: Context, r: Release, progress: (Float) -> Unit): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "UniTime-${r.version}.apk")
        val c = URL(r.apkUrl).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: r.size
            c.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        done += n
                        if (total > 0) progress((done.toFloat() / total).coerceAtMost(1f))
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        if (r.size > 0 && out.length() != r.size) error("файл скачался не полностью")
        return out
    }

    /**
     * Открывает системную установку. Если UniTime ещё не разрешено ставить приложения —
     * открывает этот переключатель в настройках и возвращает false.
     */
    fun install(ctx: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return false
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return true
    }
}

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (java.time.LocalTime.now().hour < 9) return // не будим ночью
        val pending = goAsync()
        Thread {
            runCatching { AppUpdate.backgroundCheck(ctx) }
            pending.finish()
        }.start()
    }
}
