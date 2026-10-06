package app.morrow.debug

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.morrow.alarm.RingService
import app.morrow.domain.Alarm
import app.morrow.domain.MissionType
import app.morrow.domain.nextAlarm
import app.morrow.morrow
import app.morrow.permissions.AlarmPermission
import app.morrow.permissions.AlarmPermissions
import app.morrow.theme.MorrowTheme
import app.morrow.theme.SkyCurve
import app.morrow.theme.ThemeKind
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/** Temporary screen for testing the alarm engine. Phase 5 replaces it with Home. */
@Composable
fun DebugScreen(resumeCount: Int) {
    val context = LocalContext.current
    val app = context.morrow
    val scope = rememberCoroutineScope()
    val alarms by app.repository.alarms.collectAsState(initial = emptyList())
    val permissions = remember { AlarmPermissions(context) }
    // Re-checked each time the user comes back from a settings screen.
    val missing = remember(resumeCount) { permissions.missing() }

    val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) context.startActivity(permissions.settingsIntent(AlarmPermission.NOTIFICATIONS))
    }

    fun fix(permission: AlarmPermission) {
        if (permission == AlarmPermission.NOTIFICATIONS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(permissions.settingsIntent(permission))
        }
    }

    val zone = TimeZone.currentSystemDefault()
    val next = alarms.nextAlarm(Clock.System.now(), zone)

    val colors = MorrowTheme.colors
    val text = MorrowTheme.type.body.copy(color = colors.text)
    val themeClock = app.themeClock
    val pinned by themeClock.pinnedTheme.collectAsState()
    val theme by themeClock.theme.collectAsState()
    val minuteNow = Clock.System.now().toLocalDateTime(zone).let { it.hour * 60 + it.minute }

    Column(
        modifier = Modifier.fillMaxSize().background(colors.bg).safeDrawingPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText("Morrow debug", style = MorrowTheme.type.title.copy(color = colors.text))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(32.dp).background(SkyCurve.at(minuteNow.toFloat()), CircleShape))
            BasicText("Sky now · theme: ${theme.displayName}", style = text)
        }
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            DebugButton("Auto", selected = pinned == null) { themeClock.pin(null) }
            for (kind in ThemeKind.entries) {
                DebugButton(kind.name.lowercase().replaceFirstChar(Char::uppercase), selected = pinned == kind) {
                    themeClock.pin(kind)
                }
            }
        }

        for (permission in missing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val need = if (permission.required) "Alarms can't ring properly" else "Recommended on this phone"
                BasicText("$need: ${permission.name.lowercase().replace('_', ' ')}", style = MorrowTheme.type.body.copy(color = colors.warn), modifier = Modifier.weight(1f))
                DebugButton("Allow") { fix(permission) }
            }
        }

        BasicText(
            next?.let {
                val at = it.at.toLocalDateTime(zone)
                "Next: ${at.dayOfWeek.short()} ${hhmm(at.hour, at.minute)} (${it.alarm.label})"
            } ?: "No alarms on",
            style = text,
        )

        Row {
            DebugButton("Add alarm in 1 min") {
                val inAMinute = (Clock.System.now() + 1.minutes).toLocalDateTime(zone)
                val alarm = Alarm(0, inAMinute.hour, inAMinute.minute, "Test", emptySet(), MissionType.SUMS, enabled = true)
                scope.launch { app.coordinator.save(alarm) }
            }
            DebugButton("Ring now") { RingService.start(context, alarms.firstOrNull()?.id ?: -1) }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(alarms, key = Alarm::id) { alarm ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        "${hhmm(alarm.hour, alarm.minute)} · ${alarm.label} · ${alarm.days.describe()}",
                        style = text,
                        modifier = Modifier.weight(1f),
                    )
                    DebugButton(if (alarm.enabled) "On" else "Off") {
                        scope.launch { app.coordinator.setEnabled(alarm.id, !alarm.enabled) }
                    }
                    DebugButton("Delete") { scope.launch { app.coordinator.delete(alarm.id) } }
                }
            }
        }
    }
}

private fun hhmm(hour: Int, minute: Int) = "%02d:%02d".format(hour, minute)

private fun DayOfWeek.short() = name.take(3).lowercase().replaceFirstChar(Char::uppercase)

private fun Set<DayOfWeek>.describe() = when {
    isEmpty() -> "Once"
    size == 7 -> "Every day"
    else -> sorted().joinToString(" ") { it.short() }
}
