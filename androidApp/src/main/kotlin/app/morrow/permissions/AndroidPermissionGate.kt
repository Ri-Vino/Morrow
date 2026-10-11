package app.morrow.permissions

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import app.morrow.platform.AlarmPermission
import app.morrow.platform.AndroidPermissions
import app.morrow.platform.PermissionGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Create in the activity's onCreate: it registers for the notification and location permission results. */
class AndroidPermissionGate(private val activity: ComponentActivity) : PermissionGate {

    private val checks = AlarmPermissions(activity)
    override val asked: List<AlarmPermission> = AndroidPermissions
    private val state = MutableStateFlow(checks.missing())
    override val missing: StateFlow<List<AlarmPermission>> = state.asStateFlow()

    private val notificationRequest = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Once the user has said no twice, Android stops asking, so send them to the settings screen instead.
        if (!granted) activity.startActivity(checks.settingsIntent(AlarmPermission.NOTIFICATIONS))
        refresh()
    }

    // Whether Android would have explained the location request before it was made, to tell a refusal from no dialog at all.
    private var locationRationaleBefore = false

    private val locationRequest = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Location stays optional, so a "no" is left alone. Only when Android didn't show the dialog at all, because the
        // user said no before, does the settings screen open, as the Allow button must do something.
        val dialogSkipped = !locationRationaleBefore && !activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!granted && dialogSkipped) activity.startActivity(checks.settingsIntent(AlarmPermission.LOCATION))
        refresh()
    }

    override fun refresh() {
        state.value = checks.missing()
    }

    override fun request(permission: AlarmPermission) {
        if (permission == AlarmPermission.NOTIFICATIONS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (permission == AlarmPermission.LOCATION) {
            locationRationaleBefore = activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
            locationRequest.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        } else {
            activity.startActivity(checks.settingsIntent(permission))
        }
    }
}
