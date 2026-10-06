package app.morrow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.morrow.theme.MorrowTheme
import app.morrow.theme.ThemeKind

// The same times as the design canvas's artboards.

@Preview(widthDp = 390, heightDp = 330)
@Composable
private fun SkySceneMorning() = ScenePreview(ThemeKind.MORNING, 7, 40)

@Preview(widthDp = 390, heightDp = 330)
@Composable
private fun SkySceneNoon() = ScenePreview(ThemeKind.NOON, 12, 30)

@Preview(widthDp = 390, heightDp = 330)
@Composable
private fun SkySceneAfternoon() = ScenePreview(ThemeKind.AFTERNOON, 16, 45)

@Preview(widthDp = 390, heightDp = 330)
@Composable
private fun SkySceneNight() = ScenePreview(ThemeKind.NIGHT, 21, 40)

@Preview(widthDp = 390, heightDp = 330)
@Composable
private fun SkySceneMidnight() = ScenePreview(ThemeKind.NIGHT, 1, 20)

@Preview(widthDp = 390, heightDp = 96)
@Composable
private fun PipLooks() = MorrowTheme(ThemeKind.NIGHT) {
    Row(
        modifier = Modifier.background(MorrowTheme.colors.bg).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val looks = PipMood.entries.map { PipLook(it, rays = it != PipMood.ASLEEP && it != PipMood.YAWNING) } +
            PipLook(PipMood.ASLEEP, moon = true, nightcap = true)
        looks.forEach { Pip(it, Modifier.size(56.dp)) }
    }
}

@Composable
private fun ScenePreview(theme: ThemeKind, hour: Int, minute: Int) = MorrowTheme(theme) {
    SkyScene(minuteOfDay = hour * 60f + minute, theme = theme)
}
