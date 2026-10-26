package com.jarvis.assistant

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Séquence de démarrage façon interface holographique, dessinée sur Canvas :
 *  0 – 1,8 s  anneaux qui apparaissent et barre de chargement 0 → 100 %
 *  1,8 – 3,6 s  le cœur hexagonal s'allume, passe du blanc au cyan
 *  3,6 – 5,6 s  « Initialisation du système… » puis « Systèmes en ligne »
 * Toucher l'écran passe la séquence.
 */
class BootSequenceView(context: Context, private val onFinished: () -> Unit) : View(context) {

    private val bg = Color.parseColor("#050B10")
    private val core = Color.parseColor("#4FD8FF")
    private val dim = Color.parseColor("#7D9AA6")

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(60f, BlurMaskFilter.Blur.NORMAL)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val arc = RectF()
    private val hex = Path()

    private var t = 0f
    private var done = false
    private val animator = ValueAnimator.ofFloat(0f, BootSound.DURATION_MS.toFloat()).apply {
        duration = BootSound.DURATION_MS.toLong()
        interpolator = LinearInterpolator()
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) = finish()
        })
    }

    init {
        setBackgroundColor(bg)
        setLayerType(LAYER_TYPE_SOFTWARE, null) // nécessaire au flou du halo
        setOnClickListener { animator.cancel(); finish() }
        contentDescription = "Démarrage de Jarvis. Touchez pour passer."
    }

    fun start() = animator.start()

    private fun finish() {
        if (done) return
        done = true
        animate().alpha(0f).setDuration(350).withEndAction(onFinished).start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height * 0.42f
        val r = min(width, height) * 0.30f
        val sec = t / 1000f

        // Halo du cœur, de plus en plus fort après l'allumage.
        val power = ((sec - 1.8f) / 1.2f).coerceIn(0f, 1f)
        if (power > 0f) {
            glow.color = withAlpha(core, (90 * power * (0.8f + 0.2f * sin(sec * 6f))).toInt())
            canvas.drawCircle(cx, cy, r * 0.9f, glow)
        }

        // Anneaux extérieurs : apparaissent pendant le chargement, puis tournent.
        val appear = (sec / 0.8f).coerceIn(0f, 1f)
        val ringColor = blend(dim, core, power)
        stroke.color = withAlpha(ringColor, (200 * appear).toInt())
        stroke.strokeWidth = r * 0.02f
        circleArc(canvas, cx, cy, r * 1.15f, 0f, 360f * appear)
        stroke.strokeWidth = r * 0.05f
        for (k in 0 until 6) {
            val start = k * 60f + sec * 40f
            circleArc(canvas, cx, cy, r * 1.0f, start, 38f * appear)
        }
        stroke.strokeWidth = r * 0.015f
        for (k in 0 until 3) {
            circleArc(canvas, cx, cy, r * 0.82f, k * 120f - sec * 70f, 80f * appear)
        }
        // Repères en losange, comme sur une mire.
        fill.color = withAlpha(ringColor, (220 * appear).toInt())
        for (k in 0 until 4) {
            val a = Math.toRadians(k * 90.0 + 45).toFloat()
            canvas.drawCircle(cx + cos(a) * r * 1.3f, cy + sin(a) * r * 1.3f, r * 0.025f, fill)
        }

        // Barre de chargement 0 → 100 %.
        if (sec < 2.1f) {
            val fade = 1f - ((sec - 1.8f) / 0.3f).coerceIn(0f, 1f)
            val progress = (sec / 1.8f).coerceIn(0f, 1f)
            val w = r * 1.5f
            val h = r * 0.22f
            stroke.color = withAlpha(Color.WHITE, (230 * fade).toInt())
            stroke.strokeWidth = r * 0.02f
            canvas.drawRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, stroke)
            fill.color = withAlpha(Color.WHITE, (200 * fade).toInt())
            canvas.drawRect(cx - w / 2 + 6, cy - h / 2 + 6, cx - w / 2 + 6 + (w - 12) * progress, cy + h / 2 - 6, fill)
            text.color = withAlpha(core, (255 * fade).toInt())
            text.textSize = r * 0.16f
            canvas.drawText("${(progress * 100).toInt()} %", cx, cy + h * 1.6f, text)
        }

        // Cœur hexagonal : grossit puis s'allume.
        if (sec >= 1.8f) {
            val grow = ((sec - 1.8f) / 0.5f).coerceIn(0f, 1f)
            val hr = r * 0.42f * grow
            hexagon(cx, cy, hr, sec * 20f)
            stroke.color = blend(Color.WHITE, core, power)
            stroke.strokeWidth = r * 0.03f
            canvas.drawPath(hex, stroke)
            hexagon(cx, cy, hr * 0.6f, -sec * 30f)
            fill.color = withAlpha(blend(Color.WHITE, core, power), (255 * grow).toInt())
            canvas.drawPath(hex, fill)
            fill.color = bg
            canvas.drawCircle(cx, cy, hr * 0.18f, fill)
        }

        // Panneau de texte : écriture lettre par lettre.
        if (sec >= 3.6f) {
            val line = if (sec < 5.0f) "INITIALISATION DU SYSTÈME" else "SYSTÈMES EN LIGNE"
            val typed = if (sec < 5.0f) ((sec - 3.6f) / 0.9f * line.length).toInt().coerceIn(0, line.length) else line.length
            val dots = if (sec < 5.0f) ".".repeat(((sec * 3).toInt() % 4)) else ""
            val y = height * 0.80f
            stroke.color = core
            stroke.strokeWidth = 3f
            canvas.drawLine(width * 0.08f, y - r * 0.22f, width * 0.92f, y - r * 0.22f, stroke)
            canvas.drawLine(width * 0.08f, y + r * 0.12f, width * 0.92f, y + r * 0.12f, stroke)
            text.color = Color.WHITE
            text.textSize = min(width, height) * 0.045f
            canvas.drawText(line.take(typed) + dots, cx, y, text)
        }
    }

    private fun circleArc(canvas: Canvas, cx: Float, cy: Float, radius: Float, start: Float, sweep: Float) {
        arc.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(arc, start, sweep, false, stroke)
    }

    private fun hexagon(cx: Float, cy: Float, radius: Float, rotation: Float) {
        hex.reset()
        for (k in 0 until 6) {
            val a = Math.toRadians((k * 60 + rotation).toDouble())
            val x = cx + (cos(a) * radius).toFloat()
            val y = cy + (sin(a) * radius).toFloat()
            if (k == 0) hex.moveTo(x, y) else hex.lineTo(x, y)
        }
        hex.close()
    }

    private fun withAlpha(color: Int, alpha: Int) =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun blend(a: Int, b: Int, p: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * p).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * p).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * p).toInt(),
    )
}
