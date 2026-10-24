package com.molex.nextpair

import android.Manifest
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.molex.nextpair.ui.BottomNav
import com.molex.nextpair.ui.C
import com.molex.nextpair.ui.DeadlineEditor
import com.molex.nextpair.ui.DeadlinesScreen
import com.molex.nextpair.ui.EmptyCard
import com.molex.nextpair.ui.ExamsScreen
import com.molex.nextpair.ui.FeedScreen
import com.molex.nextpair.ui.FriendsScreen
import com.molex.nextpair.ui.LessonScreen
import com.molex.nextpair.ui.OtherScreen
import com.molex.nextpair.ui.SearchScreen
import com.molex.nextpair.ui.SettingsScreen
import com.molex.nextpair.ui.Tab
import com.molex.nextpair.ui.TodayScreen
import com.molex.nextpair.ui.newDeadline
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/** Экран поверх вкладок. */
sealed interface Overlay {
    data class LessonOf(val o: Occurrence, val own: Boolean) : Overlay
    data class Other(val t: Target) : Overlay
    data object SettingsPage : Overlay
    data object Friends : Overlay
    data object Account : Overlay
    data object Exams : Overlay
    data object Stats : Overlay
    data object IisAccount : Overlay
}

class MainActivity : ComponentActivity() {
    private lateinit var repo: Repo
    private lateinit var st: Settings
    private lateinit var iis: Iis
    private var schedule by mutableStateOf<Schedule?>(null)
    private var loading by mutableStateOf(false)
    private var deadlines by mutableStateOf<List<Deadline>>(emptyList())
    private var pendingCalendarAdd: Boolean? = null
    /** Новая версия, о которой показываем окно. */
    private var update by mutableStateOf<AppUpdate.Release?>(null)

    private val calendarPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val add = pendingCalendarAdd
        pendingCalendarAdd = null
        if (add != null && res.values.all { it }) calendar(add)
        else if (add != null) toast("Без доступа к календарю не получится")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = Repo(this)
        st = Settings(this)
        iis = Iis(this)
        applyLook()
        schedule = repo.load()
        deadlines = repo.deadlines()

        // Кто ставил старые версии, уже выбрал группу — приветствие им не показываем.
        if (!st.onboarded && repo.hasCache) st.onboarded = true
        if (st.onboarded) askNotifications()

        load(force = false)
        IisWatch.schedule(this)
        AppUpdate.schedule(this)
        checkUpdate(force = intent?.getBooleanExtra("showUpdate", false) == true, manual = false)
        setContent {
            val scheme = if (C.Dark) darkColorScheme(primary = C.Accent, background = C.Ground, surface = C.Card)
            else lightColorScheme(primary = C.Accent, background = C.Ground, surface = C.Card)
            MaterialTheme(colorScheme = scheme) {
                if (st.onboarded) App()
                else Box(Modifier.fillMaxSize().background(C.Ground).statusBarsPadding()) {
                    com.molex.nextpair.ui.OnboardingScreen(repo, iis) { group, sub ->
                        repo.group = group
                        repo.subgroup = sub
                        st.onboarded = true
                        load(force = true)
                        askNotifications()
                    }
                }
            }
        }
    }

    /** [force] — показать окно даже после «Позже» (нажали на уведомление). [manual] — из настроек, с ответом тостом. */
    private fun checkUpdate(force: Boolean, manual: Boolean) {
        if (!manual && !st.updateCheck) return
        Thread {
            val r = runCatching { AppUpdate.checkNewer(this) }
            runOnUiThread {
                val rel = r.getOrNull()
                when {
                    rel == null && manual -> toast(if (r.isFailure) "Не удалось проверить: нет связи" else "У тебя последняя версия ${AppUpdate.current(this)}")
                    rel == null -> {}
                    force || manual || rel.mandatory -> update = rel
                    else -> {
                        val (ver, until) = st.updateSnooze.split("|").let { it.getOrElse(0) { "" } to (it.getOrNull(1)?.toLongOrNull() ?: 0L) }
                        if (ver != rel.version || System.currentTimeMillis() > until) update = rel
                    }
                }
            }
        }.start()
    }

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    private fun applyLook() {
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        C.apply(st.theme, systemDark, st.accent, st.shortNames, st.pairNumbers)
        val bar = { c: Int -> if (C.Dark) SystemBarStyle.dark(c) else SystemBarStyle.light(c, c) }
        enableEdgeToEdge(statusBarStyle = bar(C.Ground.toArgb()), navigationBarStyle = bar(C.Card.toArgb()))
    }

    private fun unlockNeon() {
        if (st.neonUnlocked && st.theme == 4) return toast("Ты уже в неоне ✨")
        val first = !st.neonUnlocked
        st.neonUnlocked = true
        st.theme = 4
        applyLook()
        reloadLocal()
        toast(if (first) "Секретная тема «Неон» открыта! Вернуть обычную — в настройках" else "Снова неон ✨")
    }

    private fun toast(text: String) = runOnUiThread { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }

    private fun load(force: Boolean) {
        loading = true
        Updater.refresh(this, forceFetch = force) { err ->
            runOnUiThread {
                schedule = repo.load()
                loading = false
                if (err != null) toast("Не удалось обновить: ${err.message}")
                else if (force) toast("Расписание обновлено")
            }
        }
    }

    /** Перечитать скрытые пары и перерисовать уведомление/виджет. */
    private fun reloadLocal() {
        schedule = repo.load()
        deadlines = repo.deadlines()
        Thread { Updater.render(this) }.start()
    }

    private fun calendar(add: Boolean) {
        val perms = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        if (perms.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
            pendingCalendarAdd = add
            calendarPermission.launch(perms)
            return
        }
        val s = schedule ?: return toast("Сначала загрузите расписание")
        Thread {
            runCatching {
                if (add) "Добавлено событий: ${CalendarExport.export(this, s, repo.subgroup)}"
                else "Удалено событий: ${CalendarExport.remove(this)}"
            }.onSuccess { toast(it) }.onFailure { toast("Календарь: ${it.message}") }
        }.start()
    }

    @Composable
    private fun App() {
        var tab by remember {
            mutableStateOf(if (intent?.getBooleanExtra("openAccount", false) == true) Tab.Profile else Tab.entries.getOrElse(st.startTab) { Tab.Today })
        }
        val stack = remember { mutableStateListOf<Overlay>() }
        var editing by remember { mutableStateOf<Deadline?>(null) }
        var now by remember { mutableStateOf(LocalDateTime.now()) }
        LaunchedEffect(Unit) {
            while (true) { delay(30_000); now = LocalDateTime.now() }
        }
        // Состояние каждого экрана (прокрутка, выбранный день, поиск) живёт, пока экран в стеке:
        // открыл пару из ленты, вернулся — лента на том же месте.
        val holder = rememberSaveableStateHolder()
        fun keyOf(depth: Int, o: Overlay) = "$depth:$o"
        val pop = {
            holder.removeState(keyOf(stack.size, stack.last()))
            stack.removeAt(stack.lastIndex)
        }
        BackHandler(enabled = stack.isNotEmpty()) { pop() }
        // Жест «назад» на вкладке: сначала вернуться на «Сегодня», и только оттуда — выйти.
        BackHandler(enabled = stack.isEmpty() && tab != Tab.Today) { tab = Tab.Today }

        val back = { pop(); Unit }
        val openLesson = { o: Occurrence, own: Boolean -> stack.add(Overlay.LessonOf(o, own)); Unit }
        val openTarget = { t: Target -> repo.addRecent(t); stack.add(Overlay.Other(t)); Unit }
        val openSettings = { stack.add(Overlay.SettingsPage); Unit }

        Box(Modifier.fillMaxSize().background(C.Ground).statusBarsPadding()) {
            val top = stack.lastOrNull()
            holder.SaveableStateProvider(top?.let { keyOf(stack.size, it) } ?: "tab:${tab.name}") {
            when (top) {
                is Overlay.LessonOf -> LessonScreen(
                    top.o, top.own, repo, st, deadlines, now,
                    onBack = back,
                    onTeacher = openTarget,
                    onGroup = openTarget,
                    onRemindersChanged = { reloadLocal() },
                    onHidden = { reloadLocal(); back(); toast("Скрыто. Вернуть можно в настройках") },
                    onAddDeadline = {
                        val next = schedule?.nextOf(top.o.lesson.subject, now, repo.subgroup)
                        editing = newDeadline(top.o.lesson.subject, next?.startAt)
                    },
                    onDeadline = { editing = it },
                )
                is Overlay.Other -> OtherScreen(top.t, repo, now, onBack = back) { openLesson(it, false) }
                Overlay.SettingsPage -> SettingsScreen(
                    repo, st, schedule,
                    onBack = back,
                    onLookChanged = { applyLook(); reloadLocal() },
                    onGroupSaved = { load(force = true) },
                    onNotifChanged = { reloadLocal() },
                    onBattery = { askBattery() },
                    onRefresh = { load(force = true) },
                    onCalendar = { calendar(it) },
                    onAccount = { stack.add(Overlay.Account) },
                    onIisAccount = { stack.add(Overlay.IisAccount) },
                    onCheckUpdate = { checkUpdate(force = true, manual = true) },
                    onUpdateToggle = { AppUpdate.schedule(this@MainActivity) },
                    onSecretStats = {
                        if (schedule != null) stack.add(Overlay.Stats) else toast("Сначала загрузите расписание")
                    },
                )
                Overlay.Stats -> schedule?.let { s -> com.molex.nextpair.ui.SecretStatsScreen(s, repo.subgroup, now, onBack = back) } ?: LaunchedEffect(Unit) { back() }
                Overlay.Account -> com.molex.nextpair.ui.AccountScreen(iis, onBack = back, onAppSettings = openSettings)
                Overlay.IisAccount -> com.molex.nextpair.ui.IisSectionScreen(iis, Iis.Kind.Settings, onBack = back)
                Overlay.Exams -> schedule?.let { s -> ExamsScreen(s, now, onBack = back) { openLesson(it, true) } } ?: LaunchedEffect(Unit) { back() }
                Overlay.Friends -> {
                    val s = schedule
                    if (s != null) FriendsScreen(repo, s, repo.subgroup, now, onBack = back, onOpen = openTarget)
                    else LaunchedEffect(Unit) { back() }
                }
                null -> Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        val s = schedule
                        when {
                            tab == Tab.Profile -> com.molex.nextpair.ui.AccountScreen(iis, onBack = null, onAppSettings = openSettings)
                            tab == Tab.Search -> SearchScreen(repo, openTarget) { stack.add(Overlay.Friends) }
                            tab == Tab.Deadlines -> DeadlinesScreen(
                                deadlines, now,
                                onAdd = { editing = newDeadline() },
                                onEdit = { editing = it },
                                onToggle = { repo.saveDeadline(it.copy(done = !it.done)); reloadLocal() },
                            )
                            s == null && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = C.Accent) }
                            s == null -> Box(Modifier.padding(20.dp)) {
                                EmptyCard("Расписание не загружено", "Проверь интернет и номер группы в настройках (шестерёнка на вкладке «Сегодня»).")
                            }
                            tab == Tab.Today -> TodayScreen(
                                s, repo.subgroup, repo.group, now, deadlines,
                                onSettings = openSettings,
                                onExams = { stack.add(Overlay.Exams) },
                                onDeadlines = { tab = Tab.Deadlines },
                            ) { openLesson(it, true) }
                            tab == Tab.Feed -> FeedScreen(s, repo.subgroup, now, deadlines, onSecret = { unlockNeon() }) { openLesson(it, true) }
                        }
                    }
                    BottomNav(tab) { tab = it }
                }
            }
            }
        }
        update?.let { r ->
            com.molex.nextpair.ui.UpdateDialog(r) {
                st.updateSnooze = "${r.version}|${System.currentTimeMillis() + 24 * 3600_000L}"
                update = null
            }
        }
        editing?.let { d ->
            val exists = deadlines.any { it.id == d.id }
            DeadlineEditor(
                d, exists, schedule, repo.subgroup,
                onSave = { repo.saveDeadline(it); editing = null; reloadLocal() },
                onDelete = { repo.deleteDeadline(d.id); editing = null; reloadLocal() },
                onDismiss = { editing = null },
            )
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        Updater.refresh(this)
    }

    /** Samsung любит усыплять фоновые приложения — просим исключение. */
    private fun askBattery() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            toast("Уже без ограничений")
            return
        }
        startActivity(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
    }
}

class PairWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Updater.refresh(ctx) { pending.finish() }
    }
}
