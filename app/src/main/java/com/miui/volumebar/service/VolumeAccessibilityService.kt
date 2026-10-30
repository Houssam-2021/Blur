package com.miui.volumebar.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.ContextCompat
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.miui.volumebar.ui.VolumeOverlayController

class VolumeAccessibilityService : AccessibilityService() {

    private lateinit var audioManager: AudioManager
    private lateinit var overlayController: VolumeOverlayController

    // Continuous key holding handler & state
    private val keyHoldHandler = Handler(Looper.getMainLooper())
    private var keyHoldRunnable: Runnable? = null
    private var activeHoldingKey: Int? = null

    companion object {
        /** The connected service, so the settings screen can open the control center. */
        var instance: VolumeAccessibilityService? = null
            private set

        private const val INITIAL_HOLD_DELAY_MS = 250L
        private const val REPEAT_INTERVAL_MS = 50L
    }




    override fun onServiceConnected() {
        super.onServiceConnected()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        overlayController = VolumeOverlayController(this, audioManager)
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Nothing to watch: the service only listens for the volume keys
    }

    override fun onInterrupt() {
        stopKeyHold()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode

        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return super.onKeyEvent(event)
        }

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                // If it's a new press (repeatCount == 0) and not currently holding
                if (event.repeatCount == 0 && activeHoldingKey != keyCode) {
                    stopKeyHold()
                    activeHoldingKey = keyCode

                    // 1. Immediately adjust volume by 1 step & display overlay
                    val direction = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                        AudioManager.ADJUST_RAISE
                    } else {
                        AudioManager.ADJUST_LOWER
                    }
                    adjustVolumeStep(direction)

                    // 2. Schedule continuous repeating adjustment after initial hold delay
                    val repeatingRunnable = object : Runnable {
                        override fun run() {
                            adjustVolumeStep(direction)
                            keyHoldHandler.postDelayed(this, REPEAT_INTERVAL_MS)
                        }
                    }
                    keyHoldRunnable = repeatingRunnable
                    keyHoldHandler.postDelayed(repeatingRunnable, INITIAL_HOLD_DELAY_MS)
                }
                return true
            }

            KeyEvent.ACTION_UP -> {
                if (activeHoldingKey == keyCode) {
                    stopKeyHold()
                }
                return true
            }
        }

        return super.onKeyEvent(event)
    }

    private fun adjustVolumeStep(direction: Int) {
        overlayController.stepVolume(direction == AudioManager.ADJUST_RAISE)
    }

    private fun stopKeyHold() {
        keyHoldRunnable?.let { keyHoldHandler.removeCallbacks(it) }
        keyHoldRunnable = null
        activeHoldingKey = null
    }

    override fun onDestroy() {
        stopKeyHold()
        instance = null
        overlayController.destroy()
        super.onDestroy()
    }
}