package com.example.dailygoals.notify

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailygoals.MainActivity
import com.example.dailygoals.R
import com.example.dailygoals.SkritiApp
import com.example.dailygoals.engine.CheckAction
import com.example.dailygoals.model.DayStatus
import com.example.dailygoals.ui.components.AccentButton
import com.example.dailygoals.ui.theme.SkritiTheme
import java.time.LocalDate
import kotlinx.coroutines.launch

/** Shown over the lock screen by a strong warning, when Android allows full-screen alerts. */
class AlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val goalId = intent.getLongExtra(ActionReceiver.EXTRA_GOAL_ID, 0L)
        val date = intent.getStringExtra(ActionReceiver.EXTRA_DATE)?.let(LocalDate::parse)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        setContent {
            SkritiTheme {
                Column(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(text, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(32.dp))
                    AccentButton(stringResource(R.string.warn_action_done), onClick = { markDone(goalId, date) })
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.alert_open_app),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { openApp() }.padding(12.dp),
                    )
                    Text(
                        text = stringResource(R.string.alert_close),
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { finish() }.padding(12.dp),
                    )
                }
            }
        }
    }

    private fun markDone(goalId: Long, date: LocalDate?) {
        val app = application as SkritiApp
        if (goalId != 0L && date != null) {
            app.scope.launch {
                if (app.repository.statusOn(goalId, date) == DayStatus.PENDING) app.repository.applyAction(goalId, date, CheckAction.TOGGLE_DONE)
                app.warnings.run()
            }
        }
        finish()
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
    }
}
