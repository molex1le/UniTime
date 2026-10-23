package com.molex.nextpair

import android.content.Context

/** Все пользовательские настройки в одном месте (SharedPreferences). */
class Settings(ctx: Context) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // Чтение version подписывает Compose на изменения, запись — будит экран.
    private fun int(key: String, def: Int) = version.intValue.let { p.getInt(key, def) }
    private fun bool(key: String, def: Boolean) = version.intValue.let { p.getBoolean(key, def) }
    private fun put(key: String, v: Any) {
        p.edit().apply {
            when (v) {
                is Int -> putInt(key, v)
                is Boolean -> putBoolean(key, v)
                is String -> putString(key, v)
            }
        }.apply()
        version.intValue++
    }

    /** 0 — как в системе, 1 — светлая, 2 — тёмная, 3 — чёрная (AMOLED). */
    var theme: Int get() = int("theme", 0); set(v) = put("theme", v)
    /** Индекс в [ACCENTS]. */
    var accent: Int get() = int("accent", 0); set(v) = put("accent", v)
    var shortNames: Boolean get() = bool("shortNames", false); set(v) = put("shortNames", v)
    var pairNumbers: Boolean get() = bool("pairNumbers", true); set(v) = put("pairNumbers", v)
    var startTab: Int get() = int("startTab", 0); set(v) = put("startTab", v)

    var hiddenSubjects: Set<String>
        get() = version.intValue.let { p.getStringSet("hidden", emptySet())!! }
        set(v) { p.edit().putStringSet("hidden", v).apply(); version.intValue++ }

    var notifEnabled: Boolean get() = bool("notifEnabled", true); set(v) = put("notifEnabled", v)
    /** Прятать постоянное уведомление, когда сегодня пар больше нет (и в выходные). */
    var hideAfterLast: Boolean get() = bool("hideAfterLast", false); set(v) = put("hideAfterLast", v)
    var notifRoom: Boolean get() = bool("notifRoom", true); set(v) = put("notifRoom", v)
    var notifTeacher: Boolean get() = bool("notifTeacher", true); set(v) = put("notifTeacher", v)
    var remindMinutes: Int get() = int("remindMinutes", 10); set(v) = put("remindMinutes", v)
    var remindSound: Boolean get() = bool("remindSound", true); set(v) = put("remindSound", v)
    var notifyChanges: Boolean get() = bool("notifyChanges", true); set(v) = put("notifyChanges", v)

    var widgetDark: Boolean get() = bool("widgetDark", false); set(v) = put("widgetDark", v)

    companion object {
        val version = androidx.compose.runtime.mutableIntStateOf(0)
        val ACCENTS = listOf("Синий", "Зелёный", "Оранжевый", "Фиолетовый")
        val REMIND_OPTIONS = listOf(5, 10, 15, 30)

        /** Номер пары БГУИР по времени начала. */
        fun pairNumber(start: java.time.LocalTime): Int? = when (start.toString()) {
            "08:30" -> 1; "10:05" -> 2; "12:00" -> 3; "13:35" -> 4
            "15:30" -> 5; "17:05" -> 6; "18:45" -> 7; "20:20" -> 8
            else -> null
        }
    }
}
