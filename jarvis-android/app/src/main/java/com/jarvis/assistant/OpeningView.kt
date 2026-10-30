package com.jarvis.assistant

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * Ouverture de Jarvis : l'image « JARVIS · AI personal assistant » qui s'éveille. L'image
 * apparaît dans le noir, l'anneau s'allume et pulse, des arcs tournent à l'intérieur, les ondes
 * ondulent, des cercles s'élargissent sous l'anneau, puis tout s'efface vers l'accueil.
 * Touchez l'écran pour passer. [onGreet] lance la voix de bienvenue, [onFinished] la fin.
 */
class OpeningView(
    context: Context,
    private val onGreet: () -> Unit,
    private val onFinished: () -> Unit,
) : View(context) {

    private val image: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.jarvis_opening)
    private val dest = RectF()
    private var scale = 1f
    private var t = 0f
    private var greeted = false
    private var done = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val path = Path()

    private val animator = ValueAnimator.ofFloat(0f, DURATION_MS / 1000f).apply {
        duration = DURATION_MS
        interpolator = LinearInterpolator()
        addUpdateListener {
            t = it.animatedValue as Float
            if (!greeted && t * 1000 >= GREET_AT_MS) { greeted = true; onGreet() }
            invalidate()
        }
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) = finish()
        })
    }

    init {
        setBackgroundColor(Color.BLACK)
        setOnClickListener { animator.cancel(); finish() }
    }

    fun start() = animator.start()

    private fun finish() {
        if (done) return
        done = true
        if (!greeted) { greeted = true; onGreet() }
        animate().alpha(0f).setDuration(500).withEndAction(onFinished).start()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // L'image (carrée) remplit l'écran, recadrée sur les côtés, l'anneau bien au centre.
        scale = max(w / SIZE, h / SIZE)
        val size = SIZE * scale
        val left = (w - size) / 2f
        val top = (h - size) / 2f
        dest.set(left, top, left + size, top + size)
    }

    private fun x(v: Float) = dest.left + v * scale
    private fun y(v: Float) = dest.top + v * scale
    private fun s(v: Float) = v * scale

    override fun onDraw(canvas: Canvas) {
        // 1. L'image sort du noir.
        paint.alpha = (255 * smooth(t / 1.2f)).toInt()
        canvas.drawBitmap(image, null, dest, paint)
        paint.alpha = 255

        val power = smooth((t - 0.6f) / 1.0f)
        if (power <= 0f) return

        // 2. L'anneau s'allume, avec un éclair au démarrage puis un battement régulier.
        val flash = exp(-((t - 1.4f) * (t - 1.4f)) / 0.08f)
        val beat = 0.5f + 0.5f * sin(t * 2f * PI.toFloat() / 1.6f)
        glow(canvas, RING_X, RING_Y, 260f + 40f * beat, CYAN, power * (0.25f + 0.2f * beat) + 0.5f * flash)
        stroke.color = CYAN
        stroke.strokeWidth = s(5f)
        stroke.alpha = (power * (150 + 105 * beat)).toInt().coerceAtMost(255)
        canvas.drawCircle(x(RING_X), y(RING_Y), s(RING_R), stroke)

        // 3. Arcs qui tournent à l'intérieur de l'anneau, en sens contraires.
        stroke.strokeWidth = s(3f)
        stroke.color = Color.WHITE
        stroke.alpha = (power * 200).toInt()
        val inner = RectF(x(RING_X - 105f), y(RING_Y - 105f), x(RING_X + 105f), y(RING_Y + 105f))
        canvas.drawArc(inner, t * 140f, 70f, false, stroke)
        canvas.drawArc(inner, t * 140f + 180f, 70f, false, stroke)
        stroke.color = CYAN
        stroke.alpha = (power * 160).toInt()
        val core = RectF(x(RING_X - 75f), y(RING_Y - 75f), x(RING_X + 75f), y(RING_Y + 75f))
        canvas.drawArc(core, -t * 220f, 110f, false, stroke)

        // 4. Ondes lumineuses qui ondulent de part et d'autre.
        for (line in 0 until 3) {
            path.reset()
            val phase = t * (1.6f + line * 0.4f) + line
            val amp = 26f + line * 10f
            var px = 0f
            while (px <= SIZE) {
                // Les ondes s'aplatissent sous l'anneau, comme sur l'image.
                val fade = (kotlin.math.abs(px - RING_X) / 260f).coerceIn(0.25f, 1f)
                val py = WAVE_Y + line * 14f + amp * fade * sin(px / 95f + phase)
                if (px == 0f) path.moveTo(x(px), y(py)) else path.lineTo(x(px), y(py))
                px += 8f
            }
            stroke.color = BLUE
            stroke.strokeWidth = s(2f)
            stroke.alpha = (power * (110 - line * 25)).toInt()
            canvas.drawPath(path, stroke)
        }

        // 5. Cercles qui s'élargissent sous l'anneau, comme une goutte de lumière.
        for (k in 0 until 3) {
            val p = ((t / 1.8f) + k / 3f) % 1f
            stroke.color = CYAN
            stroke.strokeWidth = s(2.5f)
            stroke.alpha = (power * (1f - p) * 170).toInt()
            val rx = 40f + 260f * p
            canvas.drawOval(RectF(x(RIPPLE_X - rx), y(RIPPLE_Y - rx * 0.16f), x(RIPPLE_X + rx), y(RIPPLE_Y + rx * 0.16f)), stroke)
        }
        glow(canvas, RIPPLE_X, RIPPLE_Y, 90f, CYAN, power * (0.35f + 0.25f * beat))

        // 6. Le nom brille en dernier.
        val title = smooth((t - 2.2f) / 1.2f)
        if (title > 0f) glow(canvas, 512f, 800f, 230f, CYAN, 0.18f * title * (0.7f + 0.3f * beat))
    }

    private fun glow(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, alpha: Float) {
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        if (a == 0) return
        paint.shader = RadialGradient(
            x(cx), y(cy), s(radius),
            intArrayOf(Color.argb(a, Color.red(color), Color.green(color), Color.blue(color)), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(x(cx), y(cy), s(radius), paint)
        paint.shader = null
    }

    private fun smooth(v: Float): Float {
        val c = v.coerceIn(0f, 1f)
        return c * c * (3 - 2 * c)
    }

    companion object {
        const val DURATION_MS = 6_000L
        // La voix met une à deux secondes à être générée : on la lance tôt.
        private const val GREET_AT_MS = 2_000f
        private const val SIZE = 1024f
        private const val RING_X = 512f
        private const val RING_Y = 445f
        private const val RING_R = 135f
        private const val WAVE_Y = 520f
        private const val RIPPLE_X = 512f
        private const val RIPPLE_Y = 680f
        private val CYAN = Color.rgb(53, 231, 255)
        private val BLUE = Color.rgb(40, 140, 255)

        /** Son d'ouverture (6 s, 22 050 Hz) : nappe grave qui monte, puis carillon clair à l'allumage. */
        fun sound(): ShortArray {
            val rate = 22_050
            val n = (rate * DURATION_MS / 1000).toInt()
            val out = FloatArray(n)
            for (i in 0 until n) {
                val s = i.toFloat() / rate
                val env = (s / 1.5f).coerceAtMost(1f) * ((6f - s) / 1.2f).coerceIn(0f, 1f)
                val f = 70f + 40f * (s / 6f)
                out[i] += 0.18f * env * (sin(2 * PI * f * s).toFloat() + 0.5f * sin(2 * PI * f * 2.01f * s).toFloat())
            }
            // Carillon à l'allumage de l'anneau, puis un second plus aigu à l'apparition du nom.
            for ((at, notes) in listOf(1.35f to listOf(659f, 988f), 2.4f to listOf(1319f, 1976f))) {
                val from = (at * rate).toInt()
                for (i in from until n) {
                    val s = (i - from).toFloat() / rate
                    val decay = exp(-2.5f * s)
                    notes.forEach { note -> out[i] += 0.12f * decay * sin(2 * PI * note * s).toFloat() }
                }
            }
            return ShortArray(n) { (out[it].coerceIn(-1f, 1f) * 30000).toInt().toShort() }
        }

        const val SOUND_RATE = 22_050
    }
}
