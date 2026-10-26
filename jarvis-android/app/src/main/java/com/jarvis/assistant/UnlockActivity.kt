package com.jarvis.assistant

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager

/**
 * Écran invisible qui allume le téléphone et affiche le déverrouillage du système
 * (empreinte, visage ou code tapé par l'utilisateur). Une fois déverrouillé,
 * lance l'action qui attendait, par exemple ouvrir NewPipe.
 * Jarvis ne connaît ni ne tape jamais le code : c'est toujours l'utilisateur qui déverrouille.
 */
class UnlockActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isKeyguardLocked) { done(unlocked = true); return }
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = done(unlocked = true)
            override fun onDismissCancelled() = done(unlocked = false)
            override fun onDismissError() = done(unlocked = false)
        })
    }

    private fun done(unlocked: Boolean) {
        if (unlocked) {
            @Suppress("DEPRECATION")
            val next = intent.getParcelableExtra<Intent>(EXTRA_NEXT)
            next?.let { runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }
        finish()
    }

    companion object {
        private const val EXTRA_NEXT = "next"

        fun isLocked(context: Context) =
            context.getSystemService(KeyguardManager::class.java).isKeyguardLocked

        /** Demande le déverrouillage, puis lance [next] s'il est fourni. */
        fun request(context: Context, next: Intent? = null) {
            val intent = Intent(context, UnlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            next?.let { intent.putExtra(EXTRA_NEXT, it) }
            context.startActivity(intent)
        }
    }
}
