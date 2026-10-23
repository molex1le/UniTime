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
}

class MainActivity : ComponentActivity() {
    private lateinit var repo: Repo
    private lateinit var st: Settings
    private lateinit var iis: Iis
    private var schedule by mutableStateOf<Schedule?>(null)
    private var loading by mutableStateOf(false)
    private var deadlines by mutableStateOf<List<Deadline>>(emptyList())
    private var pendingCalendarAdd: Boolean? = null

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

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        load(force = false)
        IisWatch.schedule(this)
        setContent {
            val scheme = if (C.Dark) darkColorScheme(primary = C.Accent, background = C.Ground, surface = C.Card)
            else lightColorScheme(primary = C.Accent, background = C.Ground, surface = C.Card)
            MaterialTheme(colorScheme = scheme) { App() }
        }
    }

    private fun applyLook() {
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        C.apply(st.theme, systemDark, st.accent, st.shortNames, st.pairNumbers)
        val bar = { c: Int -> if (C.Dark) SystemBarStyle.dark(c) else SystemBarStyle.light(c, c) }
        enableEdgeToEdge(statusBarStyle = bar(C.Ground.toArgb()), navigationBarStyle = bar(C.Card.toArgb()))
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
        BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }

        val back = { stack.removeAt(stack.lastIndex); Unit }
        val openLesson = { o: Occurrence, own: Boolean -> stack.add(Overlay.LessonOf(o, own)); Unit }
        val openTarget = { t: Target -> repo.addRecent(t); stack.add(Overlay.Other(t)); Unit }
        val openSettings = { stack.add(Overlay.SettingsPage); Unit }

        Box(Modifier.fillMaxSize().background(C.Ground).statusBarsPadding()) {
            when (val top = stack.lastOrNull()) {
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
                )
                Overlay.Account -> com.molex.nextpair.ui.AccountScreen(iis, onBack = back)
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
                            tab == Tab.Profile -> com.molex.nextpair.ui.AccountScreen(iis, onBack = null)
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
                            tab == Tab.Feed -> FeedScreen(s, repo.subgroup, now, deadlines) { openLesson(it, true) }
                        }
                    }
                    BottomNav(tab) { tab = it }
                }
            }
        }
        if (schedule == null && !loading && stack.isEmpty() && tab == Tab.Today) {
            LaunchedEffect(Unit) { openSettings() }
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
