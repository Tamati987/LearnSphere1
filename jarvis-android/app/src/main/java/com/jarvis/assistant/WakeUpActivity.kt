package com.jarvis.assistant

import android.app.KeyguardManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Écran du réveil : s'allume par-dessus l'écran verrouillé et affiche l'heure et la phrase de
 * Jarvis, avec « Arrêter » et « Encore 10 min ». Le son vient de [AlarmRingService].
 */
class WakeUpActivity : AppCompatActivity() {

    private var timeView: TextView? = null
    private var messageView: TextView? = null
    private var launching = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        // Ouvert directement par une ancienne alarme : on lance la sonnerie nous-mêmes.
        val alarmId = intent.getIntExtra(JarvisAlarm.EXTRA_ID, -1)
        if (!AlarmRingService.active && alarmId != -1 && savedInstanceState == null) {
            launching = runCatching { AlarmRingService.send(this, AlarmRingService.ACTION_RING, alarmId) }.isSuccess
        }
        setContentView(layout())
    }

    override fun onStart() {
        super.onStart()
        AlarmRingService.listener = { refresh() }
        refresh()
    }

    override fun onStop() {
        if (AlarmRingService.listener != null) AlarmRingService.listener = null
        super.onStop()
    }

    private fun refresh() {
        timeView?.text = AlarmRingService.time
        messageView?.text = AlarmRingService.text
        if (AlarmRingService.active) launching = false
        // La sonnerie est terminée (ou arrêtée depuis la notification) : l'écran se ferme.
        else if (!launching) finish()
    }

    private fun command(action: String) {
        runCatching { AlarmRingService.send(this, action) }
        finish()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun layout() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(getColor(R.color.bg))
        val pad = (24 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad, pad, pad)

        addView(TextView(context).apply {
            text = "JARVIS · RÉVEIL"
            setTextColor(getColor(R.color.dim))
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.2f
            gravity = Gravity.CENTER
        })
        addView(TextView(context).apply {
            timeView = this
            setTextColor(getColor(R.color.core))
            textSize = 84f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            gravity = Gravity.CENTER
            setShadowLayer(24f, 0f, 0f, getColor(R.color.core))
        })
        addView(TextView(context).apply {
            messageView = this
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, pad, 0, pad * 2)
        })
        addView(Button(context).apply {
            text = "Arrêter"
            isAllCaps = false
            textSize = 18f
            setTextColor(getColor(R.color.on_core))
            setBackgroundColor(getColor(R.color.core))
            setOnClickListener { command(AlarmRingService.ACTION_STOP) }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (56 * resources.displayMetrics.density).toInt()))
        addView(Button(context).apply {
            text = "Encore 10 min"
            isAllCaps = false
            textSize = 16f
            setTextColor(getColor(R.color.core))
            setBackgroundColor(getColor(R.color.panel))
            setOnClickListener { command(AlarmRingService.ACTION_SNOOZE) }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (56 * resources.displayMetrics.density).toInt()).apply {
            topMargin = pad / 2
        })
    }
}
