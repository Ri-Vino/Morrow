package app.morrow.ui

import androidx.compose.ui.MotionDurationScale

/** The system's "remove animations" setting: scenes hold still, so screens settle and tests can wait for them. */
internal object NoMotion : MotionDurationScale {
    override val scaleFactor: Float = 0f
}
