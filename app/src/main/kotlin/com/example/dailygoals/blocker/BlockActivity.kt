package com.example.dailygoals.blocker

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.example.dailygoals.R
import com.example.dailygoals.SkritiApp
import java.lang.ref.WeakReference

/** Method 1 of the proof of concept: a block screen started as an activity from the accessibility service. */
class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = (application as SkritiApp).pocStore
        lastCreatedUptime = SystemClock.uptimeMillis()
        current = WeakReference(this)
        store.log("block activity opened")
        setContentView(
            buildBlockView(
                context = this,
                title = getString(R.string.poc_block_title),
                message = getString(R.string.poc_block_message, store.target),
                goHomeLabel = getString(R.string.poc_block_go_home),
                stopLabel = getString(R.string.poc_block_stop),
                onGoHome = ::goHome,
                onStop = {
                    store.stop()
                    finish()
                },
            ),
        )
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
