package com.example.dailygoals

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.dailygoals.ui.AppRoot
import com.example.dailygoals.ui.theme.DailyGoalsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setOnExitAnimationListener { splash ->
            splash.view.animate()
                .alpha(0f)
                .setDuration(SPLASH_FADE_MS)
                .withEndAction(splash::remove)
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DailyGoalsTheme {
                AppRoot()
            }
        }
    }

    private companion object {
        const val SPLASH_FADE_MS = 200L
    }
}
