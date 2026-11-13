package com.example.dailygoals.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dailygoals.R
import com.example.dailygoals.blocker.BlockRegistry
import com.example.dailygoals.blocker.BlockerService
import com.example.dailygoals.engine.EmergencyPause
import com.example.dailygoals.engine.PauseLength
import com.example.dailygoals.ui.components.SegmentedChoice
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Counts down once a second while a pause runs; returns the milliseconds left, or 0 when none runs. */
@Composable
fun rememberPauseLeft(registry: BlockRegistry): Long {
    val changes by registry.changes.collectAsStateWithLifecycle()
    var left by remember { mutableLongStateOf(0L) }
    LaunchedEffect(changes) {
        while (isActive) {
            left = EmergencyPause.millisLeft(registry.pauseUses, System.currentTimeMillis())
            if (left <= 0L) break
            delay(1000)
        }
    }
    return left
}

fun formatLeft(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(0)
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** A strip shown above every screen while the emergency pause is running. */
@Composable
fun PauseBanner(registry: BlockRegistry, modifier: Modifier = Modifier) {
    val left = rememberPauseLeft(registry)
    if (left <= 0L) return
    val scheme = MaterialTheme.colorScheme
    Text(
        text = stringResource(R.string.pause_banner, formatLeft(left)),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = scheme.onTertiaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .background(scheme.tertiaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Starting the pause takes effort on purpose: hold for five seconds, or type the word. */
@Composable
fun EmergencyPauseCard(registry: BlockRegistry) {
    val changes by registry.changes.collectAsStateWithLifecycle()
    val left = rememberPauseLeft(registry)
    val now = System.currentTimeMillis()
    val usesLeft = remember(changes, left) { EmergencyPause.usesLeftToday(registry.pauseUses, now, ZoneId.systemDefault()) }
    var length by remember { mutableStateOf(PauseLength.THIRTY_MINUTES) }
    var typed by remember { mutableStateOf("") }
    Text(stringResource(R.string.pause_help), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (left > 0L) {
        Text(
            stringResource(R.string.pause_running, formatLeft(left)),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 10.dp),
        )
        return
    }
    Text(stringResource(R.string.pause_uses_left, usesLeft), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
    if (usesLeft <= 0) return
    SegmentedChoice(
        options = listOf(
            PauseLength.THIRTY_MINUTES to stringResource(R.string.block_pause_30),
            PauseLength.ONE_HOUR to stringResource(R.string.block_pause_60),
            PauseLength.END_OF_TODAY to stringResource(R.string.block_pause_day),
        ),
        selected = length,
        onSelect = { length = it },
        modifier = Modifier.padding(top = 8.dp),
    )
    HoldToPause(Modifier.padding(top = 10.dp)) { BlockerService.startPause(registry, length) }
    OutlinedTextField(
        value = typed,
        onValueChange = { typed = it },
        label = { Text(stringResource(R.string.pause_type_label, EmergencyPause.CONFIRM_WORD)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    )
    if (EmergencyPause.isConfirmedByWord(typed)) {
        SettingsButton(stringResource(R.string.pause_start), Modifier.padding(top = 8.dp)) {
            typed = ""
            BlockerService.startPause(registry, length)
        }
    }
}

@Composable
private fun HoldToPause(modifier: Modifier, onDone: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var heldMillis by remember { mutableLongStateOf(0L) }
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceVariant)
            .border(1.dp, scheme.outline, shape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val ticker = scope.launch {
                            val started = System.currentTimeMillis()
                            while (isActive) {
                                heldMillis = System.currentTimeMillis() - started
                                if (EmergencyPause.isHoldLongEnough(heldMillis)) {
                                    heldMillis = 0L
                                    onDone()
                                    break
                                }
                                delay(100)
                            }
                        }
                        tryAwaitRelease()
                        ticker.cancel()
                        heldMillis = 0L
                    },
                )
            }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.Center) {
            val seconds = ((EmergencyPause.HOLD_MILLIS - heldMillis + 999) / 1000).toInt()
            Text(
                if (heldMillis > 0) stringResource(R.string.block_pause_holding, seconds) else stringResource(R.string.pause_hold),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
