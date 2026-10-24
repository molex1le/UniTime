package com.molex.nextpair.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.molex.nextpair.AppUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun UpdateDialog(r: AppUpdate.Release, onLater: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Float?>(null) }
    var file by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var needPermission by remember { mutableStateOf(false) }

    fun install(f: File) {
        needPermission = !AppUpdate.install(ctx, f)
    }

    AlertDialog(
        onDismissRequest = { if (!r.mandatory && (progress == null || file != null)) onLater() },
        properties = DialogProperties(dismissOnBackPress = !r.mandatory, dismissOnClickOutside = !r.mandatory),
        containerColor = C.Ground,
        title = { Text("Доступна UniTime ${r.version}", fontWeight = FontWeight.Bold, color = C.Ink) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (r.mandatory) Text("Важное обновление — без него часть функций может не работать.", fontSize = 14.sp, color = C.Error, fontWeight = FontWeight.SemiBold)
                Text(r.notes.ifBlank { "Исправления и улучшения." }, fontSize = 14.sp, color = C.Ink, lineHeight = 20.sp)
                Text("Сейчас установлена ${AppUpdate.current(ctx)} · ${"%.1f".format(r.size / 1_048_576f)} МБ", fontSize = 12.sp, color = C.Muted)
                progress?.let { p ->
                    if (file == null) {
                        ProgressBar(p, C.Segment, C.Accent)
                        Text("Скачиваю… ${(p * 100).toInt()}%", fontSize = 13.sp, color = C.Muted)
                    }
                }
                if (needPermission) Text(
                    "Разреши UniTime устанавливать приложения (переключатель на открывшемся экране), вернись и нажми «Установить».",
                    fontSize = 13.sp, color = C.Accent, lineHeight = 18.sp,
                )
                error?.let { Text("Не получилось: $it", fontSize = 13.sp, color = C.Error) }
            }
        },
        confirmButton = {
            val f = file
            TextButton(
                onClick = {
                    if (f != null) { install(f); return@TextButton }
                    if (progress != null) return@TextButton
                    error = null
                    progress = 0f
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { AppUpdate.download(ctx, r) { p -> progress = p } } }
                            .onSuccess { file = it; install(it) }
                            .onFailure { error = it.message ?: "нет связи"; progress = null }
                    }
                },
            ) {
                Text(
                    when {
                        f != null -> "Установить"
                        progress != null -> "Скачиваю…"
                        else -> "Обновить"
                    },
                    color = C.Accent, fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            if (!r.mandatory && (progress == null || file != null)) TextButton(onLater) { Text("Позже", color = C.Muted) }
        },
    )
}
