package com.molex.nextpair

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject
import java.net.HttpCookie
import java.net.HttpURLConnection
import java.net.URL

/**
 * Личный кабинет iis.bsuir.by. Тот же API, что у сайта: вход по студбилету и паролю,
 * дальше сессия в cookie. Пароль уходит только на iis.bsuir.by; сохраняется он,
 * только если включено «Запомнить пароль», и тогда — зашифрованным ключом из Android Keystore.
 */
class Iis(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("iis", Context.MODE_PRIVATE)

    /** Последние успешные ответы кабинета — чтобы разделы открывались мгновенно. Стираются при выходе. */
    private val cacheDir = java.io.File(ctx.filesDir, "iis_cache")

    sealed interface LoginResult {
        data object Ok : LoginResult
        /** Сайт просит что-то подтвердить у себя (код 280). */
        data class Redirect(val url: String) : LoginResult
        data class Failed(val status: Int, val message: String) : LoginResult
    }

    data class Response(val code: Int, val body: String)

    val loggedIn: Boolean get() = (cookies().isNotEmpty() && prefs.getBoolean("ok", false)) || hasSavedPassword
    val login: String get() = prefs.getString("login", "")!!
    val hasSavedPassword: Boolean get() = prefs.contains("pw")

    fun login(username: String, password: String, remember: Boolean, savePassword: Boolean = false): LoginResult {
        // Другой аккаунт — чужие сохранённые ответы не показываем.
        if (username != login) cacheDir.deleteRecursively()
        prefs.edit().remove("cookies").remove("ok").remove("pw").apply()
        val body = JSONObject().put("username", username).put("password", password).put("rememberDevice", remember)
        val r = request("POST", "/auth/login", body.toString())
        return when (r.code) {
            200, 201, 204 -> {
                prefs.edit().putBoolean("ok", true).putString("login", username).apply()
                if (savePassword) runCatching { prefs.edit().putString("pw", encrypt(password)).apply() }
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
        cacheDir.deleteRecursively()
    }

    private fun cacheFile(path: String): java.io.File {
        val hash = java.security.MessageDigest.getInstance("SHA-1").digest(path.toByteArray()).joinToString("") { "%02x".format(it) }
        return java.io.File(cacheDir, "$hash.json")
    }

    /** Сохранённый ответ и когда он получен (мс), если есть. */
    fun cached(path: String): Pair<Response, Long>? {
        val f = cacheFile(path)
        if (!f.exists()) return null
        return runCatching { Response(200, f.readText()) to f.lastModified() }.getOrNull()
    }

    /** Подгрузить разделы заранее, если сохранённое старше [maxAgeMs]. Вызывать не из главного потока. */
    fun prefetch(paths: List<String>, maxAgeMs: Long = 2 * 60_000L) {
        paths.forEach { p ->
            val age = cached(p)?.let { System.currentTimeMillis() - it.second } ?: Long.MAX_VALUE
            if (age > maxAgeMs) runCatching { get(p) }
        }
    }

    fun forgetPassword() = prefs.edit().remove("pw").apply()

    /**
     * GET к API кабинета. 401/403 — сессия кончилась: если пароль сохранён, входим заново
     * и повторяем запрос, иначе сбрасываем вход.
     */
    fun get(path: String): Response = fetch(path).also { r ->
        if (r.code in 200..299) runCatching {
            cacheDir.mkdirs()
            cacheFile(path).writeText(r.body)
        }
    }

    private fun fetch(path: String): Response {
        val r = request("GET", path)
        if (r.code != 401 && r.code != 403) return r
        // Перезаход один на всех: параллельные запросы ждут его и повторяют уже с новой сессией.
        synchronized(RELOGIN) {
            val retry = request("GET", path)
            if (retry.code != 401 && retry.code != 403) return retry
            prefs.edit().putBoolean("ok", false).apply()
            val pw = prefs.getString("pw", null)?.let { runCatching { decrypt(it) }.getOrNull() } ?: return r
            val again = login(login, pw, remember = true, savePassword = true)
            return if (again is LoginResult.Ok) request("GET", path) else r
        }
    }

    // --- Шифрование пароля ключом, который не покидает защищённое хранилище телефона ---

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val data = c.iv + c.doFinal(plain.toByteArray())
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val data = Base64.decode(stored, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, 12))
        return String(c.doFinal(data, 12, data.size - 12))
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
        private const val KEY_ALIAS = "nextpair_iis"
        private val RELOGIN = Any()

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
