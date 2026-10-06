package app.morrow.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morrow.domain.MissionType
import app.morrow.theme.ThemeKind

/** A line icon from the design, drawn from 24 × 24 SVG path data. Decorative unless given a [contentDescription]. */
@Composable
fun MorrowIcon(
    path: String,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    strokeWidth: Float = 2f,
    contentDescription: String? = null,
) {
    val shape = remember(path) { svgPath(path) }
    val described = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    Canvas(modifier.size(size).then(described)) {
        scale(this.size.minDimension / 24f, Offset.Zero) {
            drawPath(shape, tint, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

object Icons {
    const val Sunrise = "M3 18h18M7 14a5 5 0 0 1 10 0M12 3v4M9.5 5.5L12 3l2.5 2.5"
    const val Sun = "M12 8a4 4 0 1 0 0 8a4 4 0 0 0 0-8zM12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"
    const val Sunset = "M3 18h18M7 14a5 5 0 0 1 10 0M12 3v4M9.5 4.5L12 7l2.5-2.5"
    const val Moon = "M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z"
    const val Settings = "M4 7h10M18 7h2M4 17h4M12 17h8M18.5 7a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0M12.5 17a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0"
    const val Plus = "M12 5v14M5 12h14"
    const val Minus = "M5 12h14"
    const val Back = "M15 5l-7 7 7 7"
    const val Trash = "M4 7h16M10 11v6M14 11v6M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12M9 7V4h6v3"
    const val TrySunrise = "M16 14a4 4 0 1 1-8 0a4 4 0 1 1 8 0M3 19h18M12 4v2.5M5.2 7.6l1.7 1.7M18.8 7.6l-1.7 1.7"
    const val AlarmOff = "M9.5 4.5A6 6 0 0 1 18 10c0 3 .8 5 1.6 6.2M6.2 6.6A6 6 0 0 0 6 8c0 5-2.5 8-2.5 8H16M10.3 20a2 2 0 0 0 3.4 0M3 3l18 18"
    const val Check = "M5 12.5l4.5 4.5L19 7.5"
    const val ArrowRight = "M5 12h14M13 6l6 6-6 6"
    const val Bell = "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10.3 21a1.94 1.94 0 0 0 3.4 0"
    const val Clock = "M12 7v5l3 2M12 3a9 9 0 1 0 0 18a9 9 0 0 0 0-18z"
    const val FullScreen = "M8 3H5a2 2 0 0 0-2 2v3M16 3h3a2 2 0 0 1 2 2v3M8 21H5a2 2 0 0 1-2-2v-3M16 21h3a2 2 0 0 0 2-2v-3"
    const val Battery = "M4 7h13a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2zM22 11v2M7 10v4M11 10v4"

    fun forTheme(theme: ThemeKind) = when (theme) {
        ThemeKind.MORNING -> Sunrise
        ThemeKind.NOON -> Sun
        ThemeKind.AFTERNOON -> Sunset
        ThemeKind.NIGHT -> Moon
    }

    fun forMission(mission: MissionType) = when (mission) {
        MissionType.SUMS -> "M4 7h6M7 4v6M14 7h6M4.5 14.5l5 5M9.5 14.5l-5 5M14 15.5h6M14 18.5h6"
        MissionType.BREATHE -> "M3 9h11a3 3 0 1 0-3-3M3 13h15a3 3 0 1 1-3 3M3 17h7"
        MissionType.SHAKE -> "M9 3h6a1.5 1.5 0 0 1 1.5 1.5v15A1.5 1.5 0 0 1 15 21H9a1.5 1.5 0 0 1-1.5-1.5v-15A1.5 1.5 0 0 1 9 3zM4 8v8M20 8v8M11 18h2"
        MissionType.SCAN -> "M4 8V5a1 1 0 0 1 1-1h3M16 4h3a1 1 0 0 1 1 1v3M20 16v3a1 1 0 0 1-1 1h-3M8 20H5a1 1 0 0 1-1-1v-3M8 12h8"
    }
}
