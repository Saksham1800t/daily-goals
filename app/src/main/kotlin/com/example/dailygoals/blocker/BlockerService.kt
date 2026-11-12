package com.example.dailygoals.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.example.dailygoals.R
import com.example.dailygoals.SkritiApp
import com.example.dailygoals.engine.ForegroundAction
import com.example.dailygoals.engine.ForegroundRules

/**
 * Listens only for "a window came to the front" events and reads the package name. It never reads what
 * is on screen. What it does about the blocked app depends on the method under test.
 */
class BlockerService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val store get() = (application as SkritiApp).pocStore
    private var overlay: View? = null
    private var lastPackage: String? = null

    override fun onServiceConnected() {
        instance = this
        store.log("service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val received = SystemClock.uptimeMillis()
        val pkg = event.packageName?.toString() ?: return
        val action = ForegroundRules.decide(
            foregroundPackage = pkg,
            ownPackage = packageName,
            session = store.session(),
            blockShowing = overlay != null || BlockActivity.isShowing(),
            nowMillis = System.currentTimeMillis(),
            passThrough = passThroughPackages(),
        )
        if (pkg != lastPackage || action != ForegroundAction.NONE) {
            store.log("front: $pkg ${event.className} (event age ${received - event.eventTime} ms) -> $action")
        }
        lastPackage = pkg
        when (action) {
            ForegroundAction.SHOW_BLOCK -> show(received)
            ForegroundAction.HIDE_BLOCK -> hide()
            ForegroundAction.NONE -> Unit
        }
    }

    private fun passThroughPackages(): Set<String> = buildSet {
        add("com.android.systemui")
        runCatching { getSystemService(InputMethodManager::class.java).enabledInputMethodList.forEach { add(it.packageName) } }
    }

    /**
     * A block covers the screen but does not silence the app behind it, so a video keeps playing. Sending
     * the media pause key to whatever is playing is the closest allowed way to stop the sound.
     */
    private fun pauseMedia() {
        val audio = getSystemService(AudioManager::class.java)
        if (!audio.isMusicActive) return
        val now = SystemClock.uptimeMillis()
        runCatching {
            audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 0))
            audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE, 0))
        }.onFailure { store.log("pause key failed: ${it.javaClass.simpleName}") }
        store.log("pause key sent while sound was playing")
        handler.postDelayed({ store.log("sound still playing after the pause key: ${audio.isMusicActive}") }, PAUSE_CHECK_MILLIS)
    }

    private fun show(received: Long) {
        if (store.pauseMedia) pauseMedia()
        when (store.method) {
            BlockMethod.ACTIVITY -> launchActivity(received, thenHome = false)
            BlockMethod.OVERLAY -> showOverlay(received)
            BlockMethod.HOME -> goHome("method home")
            BlockMethod.OVERLAY_THEN_HOME -> {
                showOverlay(received)
                handler.postDelayed({ if (overlay != null) goHome("overlay then home") }, HOME_DELAY_MILLIS)
            }
            BlockMethod.ACTIVITY_THEN_HOME -> launchActivity(received, thenHome = true)
        }
    }

    private fun launchActivity(received: Long, thenHome: Boolean) {
        val before = BlockActivity.lastCreatedUptime
        val intent = Intent(this, BlockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { startActivity(intent) }.onFailure { store.log("startActivity threw: ${it.javaClass.simpleName}") }
        handler.postDelayed(
            {
                val opened = BlockActivity.lastCreatedUptime > before
                store.log(if (opened) "activity launch worked after ${BlockActivity.lastCreatedUptime - received} ms" else "activity launch did NOT open a screen")
                if (!opened && thenHome) goHome("activity failed, sending home")
            },
            LAUNCH_CHECK_MILLIS,
        )
    }

    private fun showOverlay(received: Long) {
        if (overlay != null) return
        val view = buildBlockView(
            context = this,
            title = getString(R.string.poc_block_title),
            message = getString(R.string.poc_block_message, store.target),
            goHomeLabel = getString(R.string.poc_block_go_home),
            stopLabel = getString(R.string.poc_block_stop),
            onGoHome = { goHome("go home button") },
            onStop = {
                store.stop()
                hide()
            },
        )
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE,
        ).apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS }
        runCatching { getSystemService(WindowManager::class.java).addView(view, params) }
            .onSuccess {
                overlay = view
                store.log("overlay added ${SystemClock.uptimeMillis() - received} ms after the event")
            }
            .onFailure { store.log("overlay failed: ${it.javaClass.simpleName} ${it.message}") }
    }

    private fun goHome(reason: String) {
        store.log("sending home ($reason)")
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun hide() {
        overlay?.let { view ->
            runCatching { getSystemService(WindowManager::class.java).removeView(view) }
            overlay = null
            store.log("overlay removed")
        }
        if (BlockActivity.isShowing()) {
            BlockActivity.finishCurrent()
            store.log("block activity closed")
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        store.log("service disconnected")
        hide()
        instance = null
        return super.onUnbind(intent)
    }

    companion object {
        private const val HOME_DELAY_MILLIS = 2500L
        private const val LAUNCH_CHECK_MILLIS = 700L
        private const val PAUSE_CHECK_MILLIS = 800L

        @Volatile
        var instance: BlockerService? = null
            private set
    }
}
