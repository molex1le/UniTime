package com.molex.nextpair

import android.content.Context
import org.json.JSONObject
import java.net.HttpCookie
import java.net.HttpURLConnection
import java.net.URL

/**
 * Личный кабинет iis.bsuir.by. Тот же API, что у сайта: вход по студбилету и паролю,
 * дальше сессия в cookie. Пароль нигде не сохраняется — только cookie сессии
 * (в приватном хранилище приложения), и уходит он только на iis.bsuir.by.
 */
class Iis(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("iis", Context.MODE_PRIVATE)

    sealed interface LoginResult {
        data object Ok : LoginResult
        /** Сайт просит что-то подтвердить у себя (код 280). */
        data class Redirect(val url: String) : LoginResult
        data class Failed(val status: Int, val message: String) : LoginResult
    }

    data class Response(val code: Int, val body: String)

    val loggedIn: Boolean get() = cookies().isNotEmpty() && prefs.getBoolean("ok", false)
    val login: String get() = prefs.getString("login", "")!!

    fun login(username: String, password: String, remember: Boolean): LoginResult {
        prefs.edit().clear().apply()
        val body = JSONObject().put("username", username).put("password", password).put("rememberDevice", remember)
        val r = request("POST", "/auth/login", body.toString())
        return when (r.code) {
            200, 201, 204 -> {
                prefs.edit().putBoolean("ok", true).putString("login", username).apply()
                LoginResult.Ok
            }
            280 -> LoginResult.Redirect(runCatching { JSONObject(r.body).optString("redirectUrl") }.getOrDefault(""))
            else -> {
                val o = runCatching { JSONObject(r.body) }.getOrNull()
                val ban = o?.optString("banExpiration")?.takeIf { it.isNotBlank() && it != "null" }
                val msg = when {
                    ban != null -> "Вход временно заблокирован до $ban"
                    r.code == 401 || r.code == 403 -> o?.optString("error")?.takeIf { it.isNotBlank() && it != "Unauthorized" && it != "Forbidden" }
                        ?: "Неверный номер студенческого или пароль"
                    else -> "Ошибка сервера (${r.code})"
                }
                LoginResult.Failed(r.code, msg)
            }
        }
    }

    fun logout() {
        runCatching { request("GET", "/auth/logout") }
        prefs.edit().clear().apply()
    }

    /** GET к API кабинета. 401/403 означает, что сессия кончилась — сбрасываем вход. */
    fun get(path: String): Response {
        val r = request("GET", path)
        if (r.code == 401 || r.code == 403) prefs.edit().putBoolean("ok", false).apply()
        return r
    }

    private fun cookies(): Map<String, String> =
        prefs.getStringSet("cookies", emptySet())!!.mapNotNull { it.split("=", limit = 2).takeIf { p -> p.size == 2 } }
            .associate { it[0] to it[1] }

    private fun saveCookies(c: HttpURLConnection) {
        val jar = cookies().toMutableMap()
        c.headerFields.filterKeys { it != null && it.equals("Set-Cookie", ignoreCase = true) }.values.flatten().forEach { h ->
            runCatching { HttpCookie.parse(h) }.getOrNull()?.forEach { ck ->
                if (ck.hasExpired() || ck.value.isEmpty()) jar.remove(ck.name) else jar[ck.name] = ck.value
            }
        }
        prefs.edit().putStringSet("cookies", jar.map { "${it.key}=${it.value}" }.toSet()).apply()
    }

    private fun request(method: String, path: String, json: String? = null): Response {
        val c = URL("${Repo.API}$path").openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        c.requestMethod = method
        c.connectTimeout = 15000
        c.readTimeout = 25000
        c.setRequestProperty("Accept", "application/json")
        val jar = cookies()
        if (jar.isNotEmpty()) c.setRequestProperty("Cookie", jar.entries.joinToString("; ") { "${it.key}=${it.value}" })
        jar["XSRF-TOKEN"]?.let { c.setRequestProperty("X-XSRF-TOKEN", it) }
        try {
            if (json != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(json.toByteArray()) }
            }
            val code = c.responseCode
            saveCookies(c)
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val body = stream?.bufferedReader()?.readText() ?: ""
            return Response(code, body)
        } finally {
            c.disconnect()
        }
    }

    /** Раздел кабинета как в меню сайта; некоторые собираются из нескольких запросов. */
    data class Section(val title: String, val kind: Kind, val paths: List<String>)

    enum class Kind { Profile, Markbook, Performance, Omissions, Dean, Dormitory, Group, Library, Settings, Notifications }

    companion object {
        val SECTIONS = listOf(
            Section("Профиль", Kind.Profile, listOf("/auth/user")),
            Section("Зачётка", Kind.Markbook, listOf("/markbook")),
            Section("Успеваемость", Kind.Performance, listOf("/personal-rating")),
            Section("Пропуски", Kind.Omissions, listOf("/omissions-by-student", "/disrespectful-omissions-by-student")),
            Section("Деканат", Kind.Dean, listOf("/certificate")),
            Section("Общежитие", Kind.Dormitory, listOf(
                "/dormitory-queue-application",
                "/dormitory-queue-application/privileges",
                "/dormitory-queue-application/premium-penalty",
            )),
            Section("Группа", Kind.Group, listOf("/student-groups/user-group-info")),
            Section("Библиотека", Kind.Library, listOf("/library/books")),
            Section("Уведомления", Kind.Notifications, listOf("/notifications?pageNumber=1&pageSize=30")),
            Section("Настройки", Kind.Settings, listOf("/settings/last-password-change", "/settings/password-attempts", "/settings/contacts")),
        )
    }
}
