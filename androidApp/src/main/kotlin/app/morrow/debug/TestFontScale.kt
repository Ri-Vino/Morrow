package app.morrow.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.flow.MutableStateFlow

/** Developer tool: enlarges text inside the app only, to check layouts at large font sizes without touching system settings. */
object TestFontScale {
    val extra = MutableStateFlow(1f)
}

@Composable
fun WithTestFontScale(content: @Composable () -> Unit) {
    val extra by TestFontScale.extra.collectAsState()
    val density = LocalDensity.current
    if (extra == 1f) {
        content()
    } else {
        CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * extra), content = content)
    }
}
