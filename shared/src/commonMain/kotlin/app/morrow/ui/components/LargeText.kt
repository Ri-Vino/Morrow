package app.morrow.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** True when the user's font size is large enough that side-by-side labels would break mid-word. */
@Composable
fun largeText(): Boolean = LocalDensity.current.fontScale >= 1.5f

/** [items] in one row, or two to a row with large text. */
@Composable
fun <T> TileRows(items: List<T>, modifier: Modifier = Modifier, tile: @Composable RowScope.(T) -> Unit) {
    val perRow = if (largeText()) 2 else items.size
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { tile(it) } }
        }
    }
}
