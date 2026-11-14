package com.example.dailygoals.blocker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailygoals.R
import com.example.dailygoals.SkritiApp
import com.example.dailygoals.engine.TempAccess
import com.example.dailygoals.engine.TempAccessSettings
import com.example.dailygoals.ui.components.AccentButton
import com.example.dailygoals.ui.components.SegmentedChoice
import com.example.dailygoals.ui.theme.SkritiTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The steps before one blocked app is unlocked for a few minutes: a wait, a typed reason and a length.
 * It is a normal activity because typing is not possible inside the block overlay.
 */
class TempAccessActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as SkritiApp
        val target = intent.getStringExtra(EXTRA_PACKAGE)
        val blocks = target?.let { app.blocks.blockFor(it) }
        if (target == null || blocks == null) {
            finish()
            return
        }
        val settings = app.blocks.tempSettings
        val appName = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(target, 0)).toString() }.getOrDefault(target)
        setContent {
            SkritiTheme {
                TempAccessSteps(
                    appName = appName,
                    usesLeft = app.blocks.tempUsesLeft(),
                    settings = settings,
                    onCancel = ::finish,
                    onUnlock = { reason, minutes, waited -> unlock(target, reason, minutes, waited) },
                )
            }
        }
    }

    private fun unlock(target: String, reason: String, minutes: Int, waitedSeconds: Int) {
        val app = application as SkritiApp
        val goalIds = app.blocks.blockFor(target)?.blocks.orEmpty().map { it.goalId }
        val use = app.blocks.startTempAccess(target, minutes, reason.trim(), waitedSeconds)
        if (use == null) {
            finish()
            return
        }
        app.scope.launch { app.repository.logTempAccess(use, goalIds, reason.trim(), minutes) }
        TempAccessNotifier(this).show(use)
        BlockerService.instance?.afterTempAccessStarted(use.endsAtMillis)
        packageManager.getLaunchIntentForPackage(target)?.let { runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        finish()
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"

        fun start(context: Context, packageName: String) {
            val intent = Intent(context, TempAccessActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            runCatching { context.startActivity(intent) }
        }
    }
}

@Composable
private fun TempAccessSteps(
    appName: String,
    usesLeft: Int,
    settings: TempAccessSettings,
    onCancel: () -> Unit,
    onUnlock: (reason: String, minutes: Int, waitedSeconds: Int) -> Unit,
) {
    var waited by rememberSaveable { mutableIntStateOf(0) }
    var reason by rememberSaveable { mutableStateOf("") }
    val choices = TempAccess.minuteChoices(settings)
    var minutes by rememberSaveable { mutableIntStateOf(choices.first()) }
    LaunchedEffect(Unit) {
        while (waited < settings.waitSeconds) {
            delay(1000)
            waited += 1
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.temp_title, appName), fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            pluralStringResource(R.plurals.temp_uses_left, usesLeft, usesLeft),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(24.dp))
        if (waited < settings.waitSeconds) {
            Text(stringResource(R.string.temp_wait_seconds, settings.waitSeconds - waited), fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.temp_wait_help),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it.take(MAX_REASON_LENGTH) },
                label = { Text(stringResource(R.string.temp_reason)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.temp_minutes),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
            )
            SegmentedChoice(
                options = choices.map { it to stringResource(R.string.form_grace_minutes, it) },
                selected = minutes,
                onSelect = { minutes = it },
            )
            Spacer(Modifier.height(24.dp))
            if (TempAccess.errors(settings, waited, reason, minutes).isEmpty()) {
                AccentButton(stringResource(R.string.temp_unlock, minutes), onClick = { onUnlock(reason, minutes, waited) })
            } else {
                Text(stringResource(R.string.temp_reason_needed), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            text = stringResource(R.string.temp_cancel),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 20.dp).clickable(onClick = onCancel).padding(12.dp),
        )
    }
}

private const val MAX_REASON_LENGTH = 120
