package com.example.dailygoals.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
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
    private var shownAtUptime = 0L
    private var focusRequest: AudioFocusRequest? = null
    private var launchers: Set<String> = emptySet()
    private var launchersReadAt = Long.MIN_VALUE / 2

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
            launcherPackages = launcherPackages(),
            millisSinceShown = received - shownAtUptime,
        )
        if (pkg != packageName || action != ForegroundAction.NONE) {
            store.log("front: $pkg ${event.className} (event age ${received - event.eventTime} ms) -> $action")
        }
        when (action) {
            ForegroundAction.SHOW_BLOCK -> show(received)
            ForegroundAction.HIDE_BLOCK -> hide()
            ForegroundAction.NONE -> Unit
        }
    }

    /** Every installed launcher, so recents on a second launcher is handled like the stock one. */
    private fun launcherPackages(): Set<String> {
        val now = SystemClock.uptimeMillis()
        if (now - launchersReadAt > LAUNCHER_CACHE_MILLIS) {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            launchers = runCatching {
                packageManager.queryIntentActivities(home, PackageManager.MATCH_ALL).map { it.activityInfo.packageName }.toSet()
            }.getOrDefault(launchers)
            launchersReadAt = now
        }
        return launchers
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
        takeAudioFocus(audio)
        handler.postDelayed({ store.log("sound still playing after the pause key and focus request: ${audio.isMusicActive}") }, PAUSE_CHECK_MILLIS)
    }

    /**
     * Most media apps pause when another app takes audio focus. Android 17 restricts background audio,
     * so the answer is logged to learn whether this request is allowed from the service.
     */
    private fun takeAudioFocus(audio: AudioManager) {
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { }
            .build()
        val result = runCatching { audio.requestAudioFocus(request) }.getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        store.log(
            "audio focus request: " + when (result) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> "granted"
                AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> "delayed"
                else -> "refused"
            },
        )
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) focusRequest = request
    }

    private fun releaseAudioFocus() {
        val request = focusRequest ?: return
        focusRequest = null
        runCatching { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(request) }
        store.log("audio focus released")
    }

    private fun show(received: Long) {
        shownAtUptime = received
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
        val accepted = performGlobalAction(GLOBAL_ACTION_HOME)
        store.log("sending home ($reason): accepted=$accepted")
    }

    private fun hide() {
        releaseAudioFocus()
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
        private const val LAUNCHER_CACHE_MILLIS = 60_000L

        @Volatile
        var instance: BlockerService? = null
            private set
    }
}
