package app.morrow.permissions

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import app.morrow.platform.AlarmPermission
import app.morrow.platform.PermissionGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Create in the activity's onCreate: it registers for the notification permission result. */
class AndroidPermissionGate(private val activity: ComponentActivity) : PermissionGate {

    private val checks = AlarmPermissions(activity)
    private val state = MutableStateFlow(checks.missing())
    override val missing: StateFlow<List<AlarmPermission>> = state.asStateFlow()

    private val notificationRequest = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Once the user has said no twice, Android stops asking, so send them to the settings screen instead.
        if (!granted) activity.startActivity(checks.settingsIntent(AlarmPermission.NOTIFICATIONS))
        refresh()
    }

    override fun refresh() {
        state.value = checks.missing()
    }

    override fun request(permission: AlarmPermission) {
        if (permission == AlarmPermission.NOTIFICATIONS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            activity.startActivity(checks.settingsIntent(permission))
        }
    }
}
