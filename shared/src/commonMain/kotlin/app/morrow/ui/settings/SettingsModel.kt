package app.morrow.ui.settings

import app.morrow.domain.Difficulty
import app.morrow.missions.BREATHS
import app.morrow.platform.AlarmPermission
import app.morrow.theme.ThemeKind

data class SettingsModel(
    val followClock: Boolean,
    val followDescription: String,
    /** The theme showing now: the pinned one, or the clock's. */
    val shownTheme: ThemeKind,
    val difficultyDescription: String,
    val permissions: List<PermissionStatus>,
)

data class PermissionStatus(val permission: AlarmPermission, val granted: Boolean)

fun settingsModel(
    pinned: ThemeKind?,
    byClock: ThemeKind,
    difficulty: Difficulty,
    asked: List<AlarmPermission>,
    missing: List<AlarmPermission>,
): SettingsModel {
    val shown = pinned ?: byClock
    return SettingsModel(
        followClock = pinned == null,
        followDescription = if (pinned == null) "Now showing ${shown.displayName}" else "Pinned to ${shown.displayName}",
        shownTheme = shown,
        difficultyDescription = "${difficulty.sums} sums · $BREATHS breaths · ${difficulty.shakes} shakes",
        permissions = asked.map { PermissionStatus(it, granted = it !in missing) },
    )
}

val ThemeKind.shortName: String
    get() = when (this) {
        ThemeKind.MORNING -> "Morning"
        ThemeKind.NOON -> "Noon"
        ThemeKind.AFTERNOON -> "Afternoon"
        ThemeKind.NIGHT -> "Night"
    }
