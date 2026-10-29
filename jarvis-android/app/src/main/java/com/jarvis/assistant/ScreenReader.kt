package com.jarvis.assistant

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Images envoyées à Claude : réduites et compressées, pour aller vite et coûter peu. */
object Images {
    fun jpeg(bitmap: Bitmap, maxSide: Int = 1024, quality: Int = 75): ByteArray {
        val scale = maxSide.toFloat() / maxOf(bitmap.width, bitmap.height)
        val sized = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap
        return ByteArrayOutputStream().also { sized.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
    }
}

/**
 * Service d'accessibilité de Jarvis, activé une fois par l'utilisateur : il permet à Jarvis,
 * quand on le lui demande, de faire une capture de l'écran et de lire son texte, pour décrire
 * ce qui est affiché (un jeu, une page, un message). Il n'observe rien en continu.
 */
class ScreenReader : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
        JarvisBus.line(false, "Lecture de l'écran activée.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** Capture de l'écran en JPEG (Android 11 et plus), ou null. Bloque au plus 3 secondes. */
    private fun capture(): ByteArray? {
        if (Build.VERSION.SDK_INT < 30) return null
        var result: ByteArray? = null
        val done = CountDownLatch(1)
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                runCatching {
                    val buffer = screenshot.hardwareBuffer
                    val hardware = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                    val bitmap = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                    buffer.close()
                    result = bitmap?.let { Images.jpeg(it) }
                }
                done.countDown()
            }
            override fun onFailure(errorCode: Int) {
                JarvisBus.line(false, "Capture d'écran refusée par Android (code $errorCode).")
                done.countDown()
            }
        })
        done.await(3, TimeUnit.SECONDS)
        return result
    }

    /** Appli au premier plan et texte visible, pour compléter l'image. */
    private fun screenText(): String {
        val root = rootInActiveWindow ?: return ""
        val app = root.packageName?.toString()?.let { pkg ->
            runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        }.orEmpty()
        val texts = mutableListOf<String>()
        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 30 || texts.size > 150) return
            listOfNotNull(node.text, node.contentDescription).map { it.toString().trim() }
                .filter { it.isNotEmpty() && it !in texts }.forEach { texts += it }
            for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)
        }
        walk(root, 0)
        val visible = texts.joinToString(" · ").take(3000)
        return "Appli au premier plan : $app." + if (visible.isNotEmpty()) " Texte affiché : $visible" else ""
    }

    companion object {
        @Volatile private var instance: ScreenReader? = null

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            return enabled.contains("${context.packageName}/") && instance != null
        }

        /** Texte de l'écran et capture, ou null si le service n'est pas activé. */
        fun look(): Pair<String, ByteArray?>? {
            val service = instance ?: return null
            return service.screenText() to service.capture()
        }
    }
}
