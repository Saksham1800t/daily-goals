package com.example.dailygoals.ui.permissions

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.dailygoals.R
import com.example.dailygoals.SkritiApp
import com.example.dailygoals.engine.DeliveryLevel
import com.example.dailygoals.notify.PermissionStatus
import com.example.dailygoals.ui.components.AccentButton
import com.example.dailygoals.ui.components.AppIconKind
import com.example.dailygoals.ui.components.IconButtonBox
import com.example.dailygoals.ui.theme.LocalStatusColors

/** Walks through each permission with a status tick and a button to the right settings page. */
@Composable
fun PermissionsScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val checker = remember { (context.applicationContext as SkritiApp).permissions }
    var status by remember { mutableStateOf(checker.status()) }
    var askedForNotifications by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { status = checker.status() }

    val notificationPrompt = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        askedForNotifications = true
        status = checker.status()
    }
    fun open(intent: Intent) = runCatching { context.startActivity(intent) }.onFailure { context.startActivity(checker.notificationSettingsIntent()) }

    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButtonBox(AppIconKind.CLOSE, stringResource(R.string.form_close), onClose)
            Text(
                text = stringResource(R.string.permissions_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(start = 16.dp),
            )
        }
        Text(
            text = stringResource(R.string.permissions_intro),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        DeliverySummary(status)

        PermissionRow(
            title = stringResource(R.string.permission_notifications),
            help = stringResource(R.string.permission_notifications_help),
            granted = status.notifications,
            buttonText = stringResource(R.string.permission_allow),
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedForNotifications) {
                notificationPrompt.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                open(checker.notificationSettingsIntent())
            }
        }
        if (status.notifications && !status.headsUpAllowed) {
            PermissionRow(
                title = stringResource(R.string.permission_popup),
                help = stringResource(R.string.permission_popup_help),
                granted = false,
                buttonText = stringResource(R.string.permission_open_settings),
            ) { open(checker.notificationSettingsIntent()) }
        }
        PermissionRow(
            title = stringResource(R.string.permission_exact),
            help = stringResource(R.string.permission_exact_help),
            granted = status.exactAlarms,
            buttonText = stringResource(R.string.permission_open_settings),
        ) { open(checker.exactAlarmSettingsIntent()) }
        PermissionRow(
            title = stringResource(R.string.permission_full_screen),
            help = stringResource(R.string.permission_full_screen_help),
            granted = status.fullScreen,
            buttonText = stringResource(R.string.permission_open_settings),
        ) { open(checker.fullScreenSettingsIntent()) }
        PermissionRow(
            title = stringResource(R.string.permission_battery),
            help = stringResource(R.string.permission_battery_help),
            granted = status.batteryExempt,
            buttonText = stringResource(R.string.permission_open_settings),
        ) { open(checker.batterySettingsIntent()) }

        Text(
            text = stringResource(R.string.permissions_keep_alive),
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
        AccentButton(stringResource(R.string.permissions_done), onClose, Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun DeliverySummary(status: PermissionStatus) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    val normal = stringResource(levelText(status.warningLevel))
    val strong = stringResource(levelText(status.strongLevel))
    Column(
        Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surface)
            .border(1.dp, scheme.outline, shape)
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.permissions_delivery_title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.permissions_delivery_normal, normal), fontSize = 13.5.sp, modifier = Modifier.padding(top = 6.dp))
        Text(stringResource(R.string.permissions_delivery_strong, strong), fontSize = 13.5.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

private fun levelText(level: DeliveryLevel): Int = when (level) {
    DeliveryLevel.FULL_SCREEN -> R.string.level_full_screen
    DeliveryLevel.HEADS_UP -> R.string.level_heads_up
    DeliveryLevel.NORMAL -> R.string.level_normal
    DeliveryLevel.IN_APP -> R.string.level_in_app
}

@Composable
private fun PermissionRow(title: String, help: String, granted: Boolean, buttonText: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val colors = LocalStatusColors.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surface)
            .border(1.dp, scheme.outline, shape)
            .padding(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
            Text(help, fontSize = 12.5.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            Text(
                text = stringResource(if (granted) R.string.permission_on else R.string.permission_off),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (granted) colors.done else colors.pending,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (!granted) {
            Text(
                text = buttonText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .clip(CircleShape)
                    .background(scheme.surfaceVariant)
                    .border(1.dp, scheme.outline, CircleShape)
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}
