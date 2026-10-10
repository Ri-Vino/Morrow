package app.morrow.ui.settings

import app.morrow.domain.Difficulty
import app.morrow.platform.AlarmPermission
import app.morrow.theme.ThemeKind

data class SettingsModel(
    val followClock: Boolean,
    val followDescription: String,
    /** The theme showing now: the pinned one, or the clock's. */
    val shownTheme: ThemeKind,
    /** What the chosen difficulty means for a round. */
    val difficultyLevel: String,
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
        difficultyLevel = when (difficulty) {
            Difficulty.GENTLE -> "Gentler rounds: small sums, short sentences, soft shakes."
            Difficulty.NORMAL -> "Everyday rounds: two-digit sums and short sentences."
            Difficulty.TOUGH -> "Tougher rounds: bigger sums, longer sentences, firm shakes."
        },
        difficultyDescription = "How hard each round is. The number of rounds is set on each alarm.",
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
