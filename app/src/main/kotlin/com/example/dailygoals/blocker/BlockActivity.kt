package com.example.dailygoals.blocker

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.example.dailygoals.SkritiApp
import java.lang.ref.WeakReference

/** A block screen started as an activity from the accessibility service; the same content as the overlay. */
class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as SkritiApp
        lastCreatedUptime = SystemClock.uptimeMillis()
        current = WeakReference(this)
        app.pocStore.log("block activity opened")
        val actions = BlockViewActions(
            onGoHome = ::goHome,
            onStopTest = {
                app.pocStore.stop()
                BlockerWatchdog.cancel(applicationContext)
                finish()
            },
            onPause = { length ->
                BlockerService.startPause(app.blocks, length)
                finish()
            },
        )
        val view = BlockScreens.view(this, app.blocks, BlockerService.screenPackage, actions)
        if (view == null) {
            finish()
            return
        }
        setContentView(view)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    override fun onDestroy() {
        if (current?.get() === this) current = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var lastCreatedUptime: Long = 0L
            private set

        private var current: WeakReference<BlockActivity>? = null

        fun isShowing(): Boolean = current?.get() != null

        fun finishCurrent() {
            current?.get()?.finish()
        }
    }
}
