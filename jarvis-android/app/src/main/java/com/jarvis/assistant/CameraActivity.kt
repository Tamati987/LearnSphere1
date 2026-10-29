package com.jarvis.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Les yeux de Jarvis : l'aperçu de la caméra à l'écran. Jarvis y prend une image quand on le lui
 * demande et, en mode direct, regarde toutes les quelques secondes pour commenter ce qui se passe.
 * Android n'autorise la caméra que quand cet écran est affiché : rien n'est filmé en cachette.
 */
class CameraActivity : AppCompatActivity() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var preview: PreviewView
    private var front = false
    private var live = false

    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else { JarvisBus.line(false, "Caméra refusée : Jarvis ne peut pas regarder."); finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        front = intent.getBooleanExtra(EXTRA_FRONT, false)
        live = intent.getBooleanExtra(EXTRA_LIVE, false)
        setContentView(layout())
        current = this
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else askCamera.launch(Manifest.permission.CAMERA)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        live = intent.getBooleanExtra(EXTRA_LIVE, live)
        val wantFront = intent.getBooleanExtra(EXTRA_FRONT, front)
        if (wantFront != front) { front = wantFront; startCamera() }
        scheduleLive()
    }

    override fun onDestroy() {
        if (current === this) current = null
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val usecase = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            provider.unbindAll()
            runCatching { provider.bindToLifecycle(this, selector, usecase) }
                .onFailure { JarvisBus.line(false, "Caméra indisponible : ${it.message}") }
            scheduleLive()
        }, mainExecutor)
    }

    /** Image actuelle de l'aperçu, en JPEG réduit ; à appeler sur le fil principal. */
    private fun frame(): ByteArray? = preview.bitmap?.let { Images.jpeg(it, maxSide = 768, quality = 70) }

    /** Mode direct : une image toutes les [LIVE_INTERVAL_MS], commentée quand Jarvis est libre. */
    private fun scheduleLive() {
        main.removeCallbacks(liveTick)
        if (live) main.postDelayed(liveTick, LIVE_INTERVAL_MS)
    }

    private val liveTick = Runnable {
        if (live) {
            frame()?.let { JarvisService.instance?.liveFrame(it) }
            scheduleLive()
        }
    }

    private fun layout() = FrameLayout(this).apply {
        setBackgroundColor(getColor(R.color.bg))
        preview = PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        addView(preview, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        addView(TextView(context).apply {
            text = "JARVIS · VISION"
            setTextColor(getColor(R.color.core))
            letterSpacing = 0.2f
            setPadding(32, 48, 32, 0)
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START))
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 0, 32, 48)
            addView(Button(context).apply {
                text = "Retourner"
                isAllCaps = false
                setOnClickListener { front = !front; startCamera() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(Button(context).apply {
                text = "Fermer"
                isAllCaps = false
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
    }

    companion object {
        private const val EXTRA_FRONT = "front"
        private const val EXTRA_LIVE = "live"
        private const val LIVE_INTERVAL_MS = 10_000L

        @Volatile private var current: CameraActivity? = null

        /** La caméra est-elle à l'écran ? Ses images accompagnent alors ce que dit l'utilisateur. */
        val isOpen get() = current != null

        fun intent(context: Context, front: Boolean, live: Boolean): Intent =
            Intent(context, CameraActivity::class.java)
                .putExtra(EXTRA_FRONT, front)
                .putExtra(EXTRA_LIVE, live)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /** Image actuelle de la caméra, depuis n'importe quel fil ; attend au plus [timeoutMs] qu'elle soit prête. */
        fun capture(timeoutMs: Long): ByteArray? {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val activity = current
                if (activity != null) {
                    var image: ByteArray? = null
                    val done = CountDownLatch(1)
                    activity.main.post { image = runCatching { activity.frame() }.getOrNull(); done.countDown() }
                    done.await(2, TimeUnit.SECONDS)
                    if (image != null) return image
                }
                Thread.sleep(300)
            }
            return null
        }

        fun close() { current?.let { a -> a.main.post { a.finish() } } }
    }
}
