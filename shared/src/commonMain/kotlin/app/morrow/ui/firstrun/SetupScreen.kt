package app.morrow.ui.firstrun

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morrow.platform.AlarmPermission
import app.morrow.theme.Illustration
import app.morrow.theme.MorrowTheme
import app.morrow.theme.mix
import app.morrow.ui.components.Icons
import app.morrow.ui.components.MorrowIcon
import app.morrow.ui.components.PillButton
import app.morrow.ui.components.PipLook
import app.morrow.ui.components.PipMood
import app.morrow.ui.components.PrimaryButton
import app.morrow.ui.components.SecondaryButton
import app.morrow.ui.components.drawPip
import app.morrow.ui.components.rememberSceneSeconds
import app.morrow.ui.components.svgPath

@Composable
fun SetupScreen(
    missing: List<AlarmPermission>,
    onAllow: (AlarmPermission) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MorrowTheme.colors
    val model = setupModel(missing)
    Column(modifier.fillMaxSize().background(colors.bg).statusBarsPadding()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RisingSky(model.progress, model.readyText)
            BasicText(
                "A few permissions, so I can wake you on time",
                style = MorrowTheme.type.title.copy(color = colors.text),
                modifier = Modifier.semantics { heading() },
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AlarmPermission.entries.forEach { permission ->
                    PermissionRow(permission, granted = permission !in missing) { onAllow(permission) }
                }
            }
        }
        Column(
            modifier = Modifier.navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicText(
                model.note,
                style = MorrowTheme.type.body.copy(color = colors.muted, textAlign = TextAlign.Center),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (model.ready) PrimaryButton("Continue", onContinue) else SecondaryButton("Continue for now", onContinue)
        }
    }
}

@Composable
private fun PermissionRow(permission: AlarmPermission, granted: Boolean, onAllow: () -> Unit) {
    val colors = MorrowTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.dp, colors.line, shape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).background(colors.surface2, CircleShape), contentAlignment = Alignment.Center) {
            MorrowIcon(permission.icon, colors.hi)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(permission.title, style = MorrowTheme.type.button.copy(color = colors.text))
            BasicText(permission.description, style = MorrowTheme.type.body.copy(color = colors.muted))
        }
        if (granted) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(colors.surface2, CircleShape)
                    .semantics { contentDescription = "${permission.title} allowed" },
                contentAlignment = Alignment.Center,
            ) {
                MorrowIcon(Icons.Check, colors.hi, size = 22.dp, strokeWidth = 3f)
            }
        } else {
            PillButton("Allow", onAllow, colors.accent, colors.onAccent)
        }
    }
}

/** A small night sky that turns to dawn, with Pip rising, as permissions are allowed. Decorative apart from its label. */
@Composable
private fun RisingSky(progress: Float, readyText: String) {
    val p by animateFloatAsState(progress, tween(1000))
    val target = if (progress < 0.5f) {
        mix(Illustration.dawnNight, Illustration.dawnRose, progress * 2)
    } else {
        mix(Illustration.dawnRose, Illustration.dawnGold, (progress - 0.5f) * 2)
    }
    val sky by animateColorAsState(target, tween(1000))
    val seconds = rememberSceneSeconds()
    Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(24.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = size.height / 112f
            scale(unit, Offset.Zero) {
                val width = size.width / unit
                drawRect(sky, size = Size(width, 112f))
                listOf(Offset(40f, 30f), Offset(92f, 58f), Offset(width - 92f, 24f), Offset(width - 42f, 62f), Offset(width * 0.6f, 44f))
                    .forEach { drawCircle(Illustration.starlight.copy(alpha = 1f - p), 1.3f, it) }
                translate(width / 2, 112f - p * 64f) {
                    scale(28f / 30f, Offset.Zero) {
                        drawPip(PipLook(if (p < 0.5f) PipMood.ASLEEP else PipMood.OPEN), seconds.value)
                    }
                }
                scale(width / 342f, 1f, Offset.Zero) { drawPath(SetupHill, mix(sky, Illustration.ground, 0.5f)) }
            }
        }
        BasicText(
            readyText,
            style = MorrowTheme.type.label.copy(color = Illustration.onBadge, letterSpacing = MorrowTheme.type.caption.letterSpacing),
            modifier = Modifier
                .padding(16.dp)
                .background(Illustration.badge, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

private val AlarmPermission.icon: String
    get() = when (this) {
        AlarmPermission.NOTIFICATIONS -> Icons.Bell
        AlarmPermission.EXACT_ALARMS -> Icons.Clock
        AlarmPermission.FULL_SCREEN -> Icons.FullScreen
        AlarmPermission.BATTERY -> Icons.Battery
    }

private val SetupHill = svgPath("M0 84 C70 70 140 76 210 80 C270 83 310 72 342 76 L342 116 L0 116 Z")
