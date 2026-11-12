package com.example.dailygoals.blocker

import android.accessibilityservice.AccessibilityService
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.dailygoals.MainActivity
import com.example.dailygoals.R
import com.example.dailygoals.SkritiApp
import com.example.dailygoals.engine.ForegroundAction
import com.example.dailygoals.engine.ForegroundRules
import com.example.dailygoals.notify.NotificationChannels

/**
 * Listens for "a window came to the front" events. For each one it asks Android which window is really
 * active and reads only its package name, never what is on screen, and decides from that. What it does
 * about the blocked app depends on the method under test.
 */
class BlockerService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val store get() = (application as SkritiApp).pocStore
    private var overlay: View? = null
    private var homeSentAtUptime = Long.MIN_VALUE / 2
    private var shownAtUptime = 0L
    private var focusRequest: AudioFocusRequest? = null
    private var focusTried = false
    private var pauseStartedAt = 0L
    private var launchPendingUntil = 0L
    private var launchers: Set<String> = emptySet()
    private var launchersReadAt = Long.MIN_VALUE / 2

    override fun onServiceConnected() {
        instance = this
        store.log("service connected")
        if (store.keepAlive) startKeepAlive()
        if (store.session() != null) BlockerWatchdog.schedule(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        evaluate(pkg, event.className, event.eventTime, attempt = 0)
    }

    /**
     * The package in a window event is not proof that the app is in front: a floating window or a
     * launcher animation reports itself too. The active window is read at the moment of the event and
     * decides. Android sometimes has not updated it yet, so an empty answer is retried a few times.
     */
    private fun evaluate(pkg: String, className: CharSequence?, eventTime: Long, attempt: Int) {
        val session = store.session()
        val showing = overlay != null || BlockActivity.isShowing() || SystemClock.uptimeMillis() < launchPendingUntil
        if (session == null && !showing) return
        val active = activeWindowPackage(session?.targetPackage)
        if (active == null && attempt < MAX_ACTIVE_RETRIES && (pkg == session?.targetPackage || showing)) {
            handler.postDelayed({ evaluate(pkg, className, eventTime, attempt + 1) }, ACTIVE_RETRY_MILLIS)
            return
        }
        val now = SystemClock.uptimeMillis()
        val action = ForegroundRules.decide(
            foregroundPackage = pkg,
            ownPackage = packageName,
            session = session,
            blockShowing = showing,
            nowMillis = System.currentTimeMillis(),
            passThrough = passThroughPackages(),
            launcherPackages = launcherPackages(),
            millisSinceShown = now - shownAtUptime,
            millisSinceHome = now - homeSentAtUptime,
            activeWindowPackage = active,
        )
        if (pkg != packageName || action != ForegroundAction.NONE) {
            store.log("front: $pkg $className, active window $active (event age ${now - eventTime} ms) -> $action")
        }
        when (action) {
            ForegroundAction.SHOW_BLOCK -> show(eventTime)
            ForegroundAction.HIDE_BLOCK -> hide()
            ForegroundAction.NONE -> Unit
        }
    }

    /**
     * In split-screen only one pane is the active window, so the blocked app in the other pane would
     * stay usable. When it has a visible window of its own (not a picture-in-picture one) it counts as
     * the active window.
     */
    @Suppress("DEPRECATION")
    private fun activeWindowPackage(target: String?): String? {
        val root = rootInActiveWindow ?: return null
        val active = try {
            root.packageName?.toString()
        } finally {
            root.recycle()
        }
        if (target == null || active == target) return active
        val shownInAnotherPane = windows.any { window ->
            window.type == AccessibilityWindowInfo.TYPE_APPLICATION && !window.isInPictureInPictureMode &&
                window.root?.let { r -> (r.packageName?.toString() == target).also { r.recycle() } } == true
        }
        return if (shownInAnotherPane) target else active
    }

    /** Every installed launcher, for the timing rules used when the active window cannot be read. */
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

    /** Runs as a foreground service so Android is less likely to stop it while the phone sits idle. */
    fun applyKeepAlive() {
        if (store.keepAlive) {
            startKeepAlive()
        } else {
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            store.log("keep-alive notification removed")
        }
    }

    private fun startKeepAlive() {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, NotificationChannels.BLOCKER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.blocker_notification_title))
            .setContentText(getString(R.string.blocker_notification_text))
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
        }
        runCatching { ServiceCompat.startForeground(this, FOREGROUND_ID, notification, type) }
            .onSuccess { store.log("keep-alive notification started") }
            .onFailure { store.log("keep-alive failed: ${it.javaClass.simpleName} ${it.message}") }
    }

    /**
     * A block covers the screen but does not silence the app behind it, so a video keeps playing. Sending
     * the media pause key to whatever is playing is the closest allowed way to stop the sound.
     */
    private fun pauseMedia() {
        val audio = getSystemService(AudioManager::class.java)
        val now = SystemClock.uptimeMillis()
        if (!audio.isMusicActive) {
            if (pauseStartedAt != 0L) {
                store.log("sound stopped ${now - pauseStartedAt} ms after the first pause key")
                pauseStartedAt = 0L
            }
            return
        }
        runCatching {
            audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 0))
            audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE, 0))
        }.onFailure { store.log("pause key failed: ${it.javaClass.simpleName}") }
        if (pauseStartedAt == 0L) {
            pauseStartedAt = now
            store.log("sound is playing behind the block: pause key sent")
        }
        takeAudioFocus(audio)
    }

    /**
     * Most media apps pause when another app takes audio focus. Android 17 restricts background audio,
     * so the answer is logged to learn whether this request is allowed from the service.
     */
    private fun takeAudioFocus(audio: AudioManager) {
        if (focusRequest != null || focusTried) return
        focusTried = true
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

    /**
     * A video often starts a moment after the app opens, so one pause at the instant the block appears is
     * too early. While the block is up, check again and pause whatever has started playing.
     */
    private val mediaGuard = object : Runnable {
        override fun run() {
            if (overlay == null && !BlockActivity.isShowing()) return
            if (store.pauseMedia) pauseMedia()
            val early = SystemClock.uptimeMillis() - shownAtUptime < MEDIA_FAST_PERIOD_MILLIS
            handler.postDelayed(this, if (early) MEDIA_GUARD_FAST_MILLIS else MEDIA_GUARD_MILLIS)
        }
    }

    private fun show(eventTime: Long) {
        shownAtUptime = SystemClock.uptimeMillis()
        if (store.pauseMedia) {
            pauseStartedAt = 0L
            pauseMedia()
            handler.removeCallbacks(mediaGuard)
            handler.postDelayed(mediaGuard, MEDIA_GUARD_FAST_MILLIS)
        }
        when (store.method) {
            BlockMethod.ACTIVITY -> launchActivity(eventTime, thenHome = false)
            BlockMethod.OVERLAY -> showOverlay(eventTime)
            BlockMethod.HOME -> goHome("method home")
            BlockMethod.OVERLAY_THEN_HOME -> {
                showOverlay(eventTime)
                handler.postDelayed({ if (overlay != null) goHome("overlay then home") }, HOME_DELAY_MILLIS)
            }
            BlockMethod.ACTIVITY_THEN_HOME -> launchActivity(eventTime, thenHome = true)
            BlockMethod.HOME_THEN_ACTIVITY -> {
                goHome("home first")
                handler.postDelayed({ launchActivity(eventTime, thenHome = false) }, HOME_THEN_ACTIVITY_MILLIS)
            }
        }
    }

    private fun launchActivity(eventTime: Long, thenHome: Boolean) {
        launchPendingUntil = SystemClock.uptimeMillis() + LAUNCH_PENDING_MILLIS
        val before = BlockActivity.lastCreatedUptime
        val intent = Intent(this, BlockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { startActivity(intent) }.onFailure { store.log("startActivity threw: ${it.javaClass.simpleName}") }
        handler.postDelayed(
            {
                val opened = BlockActivity.lastCreatedUptime > before
                store.log(if (opened) "activity launch worked after ${BlockActivity.lastCreatedUptime - eventTime} ms" else "activity launch did NOT open a screen")
                if (!opened && thenHome) goHome("activity failed, sending home")
            },
            LAUNCH_CHECK_MILLIS,
        )
    }

    private fun showOverlay(eventTime: Long) {
        if (overlay != null) return
        val view = buildBlockView(
            context = this,
            title = getString(R.string.poc_block_title),
            message = getString(R.string.poc_block_message, store.target),
            goHomeLabel = getString(R.string.poc_block_go_home),
            stopLabel = getString(R.string.poc_block_stop),
            onGoHome = { goHome("go home button") },
            onStop = ::stopTest,
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
                store.log("overlay added ${SystemClock.uptimeMillis() - eventTime} ms after the event")
            }
            .onFailure { store.log("overlay failed: ${it.javaClass.simpleName} ${it.message}") }
    }

    private fun stopTest() {
        store.stop()
        BlockerWatchdog.cancel(this)
        hide()
    }

    /** One Home press at most every so often, so a loop of blocks can never stack up Home actions. */
    private fun goHome(reason: String) {
        val now = SystemClock.uptimeMillis()
        if (now - homeSentAtUptime < HOME_THROTTLE_MILLIS) {
            store.log("home ignored ($reason): sent just now")
            return
        }
        val accepted = performGlobalAction(GLOBAL_ACTION_HOME)
        store.log("sending home ($reason): accepted=$accepted")
        if (accepted) {
            homeSentAtUptime = now
            handler.post { hide() }
        }
    }

    private fun hide() {
        handler.removeCallbacks(mediaGuard)
        pauseStartedAt = 0L
        focusTried = false
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
        private const val FOREGROUND_ID = 4201
        private const val HOME_DELAY_MILLIS = 2500L
        private const val HOME_THEN_ACTIVITY_MILLIS = 150L
        private const val HOME_THROTTLE_MILLIS = 700L
        private const val LAUNCH_CHECK_MILLIS = 700L
        private const val LAUNCH_PENDING_MILLIS = 600L
        private const val MEDIA_GUARD_MILLIS = 700L
        private const val MEDIA_GUARD_FAST_MILLIS = 250L
        private const val MEDIA_FAST_PERIOD_MILLIS = 5000L
        private const val LAUNCHER_CACHE_MILLIS = 60_000L
        private const val ACTIVE_RETRY_MILLIS = 120L
        private const val MAX_ACTIVE_RETRIES = 3

        @Volatile
        var instance: BlockerService? = null
            private set
    }
}
