package com.jarvis.assistant

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Séquence de démarrage façon interface holographique, dessinée sur Canvas.
 * Les phases suivent [BootSound] :
 *  0 – 3 s      cadre gris en losange, barre rayée qui se remplit, compteur 0 → 100
 *  3 – 5,5 s    anneaux et cœur hexagonal blanc qui tourne
 *  5,5 – 8,5 s  balayage rouge, le cœur passe en mode alerte et pulse
 *  8,5 – 11,5 s panneau bleu « INITIALISATION DU SYSTÈME 1… », blocs qui se remplissent
 *  11,5 – 15 s  cercles cyan qui s'assemblent autour de « J.A.R.V.I.S. »
 * [onGreet] est appelé peu avant les cercles, [onFinished] à la fin.
 * Toucher l'écran passe la séquence.
 */
class BootSequenceView(
    context: Context,
    private val onGreet: () -> Unit,
    private val onFinished: () -> Unit,
) : View(context) {

    private val black = Color.parseColor("#020406")
    private val grey = Color.parseColor("#8A9399")
    private val red = Color.parseColor("#E0182D")
    private val blue = Color.parseColor("#6FD6FF")
    private val cyan = Color.parseColor("#3FD0E0")

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(40f, BlurMaskFilter.Blur.NORMAL) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.12f
    }
    private val arc = RectF()
    private val path = Path()

    private var t = 0f
    private var greeted = false
    private var done = false
    private val animator = ValueAnimator.ofFloat(0f, BootSound.DURATION_MS.toFloat()).apply {
        duration = BootSound.DURATION_MS.toLong()
        interpolator = LinearInterpolator()
        addUpdateListener {
            t = it.animatedValue as Float
            if (!greeted && t >= GREET_AT_MS) { greeted = true; onGreet() }
            invalidate()
        }
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) = finish()
        })
    }

    init {
        setBackgroundColor(black)
        setLayerType(LAYER_TYPE_SOFTWARE, null) // nécessaire aux halos flous
        setOnClickListener { animator.cancel(); finish() }
        contentDescription = "Démarrage de Jarvis. Touchez pour passer."
    }

    fun start() = animator.start()

    private fun finish() {
        if (done) return
        done = true
        if (!greeted) { greeted = true; onGreet() }
        animate().alpha(0f).setDuration(400).withEndAction(onFinished).start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val s = t / 1000f
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) * 0.30f
        grid(canvas, s)
        when {
            s < 3f -> loading(canvas, cx, cy, r, s)
            s < 5.5f -> whiteCore(canvas, cx, cy, r, s)
            s < 8.5f -> redCore(canvas, cx, cy, r, s)
            s < 11.5f -> initPanel(canvas, cx, cy, s)
            else -> finalRings(canvas, cx, cy, r, s)
        }
    }

    // Trame de fond très discrète.
    private fun grid(canvas: Canvas, s: Float) {
        if (s >= 8.5f && s < 11.5f) return
        stroke.color = Color.argb(14, 255, 255, 255)
        stroke.strokeWidth = 1f
        val step = min(width, height) / 18f
        var x = 0f
        while (x < width) { canvas.drawLine(x, 0f, x, height.toFloat(), stroke); x += step }
        var y = 0f
        while (y < height) { canvas.drawLine(0f, y, width.toFloat(), y, stroke); y += step }
    }

    // 0 – 3 s : cadre en losange et barre rayée avec compteur.
    private fun loading(canvas: Canvas, cx: Float, cy: Float, r: Float, s: Float) {
        val appear = ease(s / 0.6f)
        val progress = ease((s - 0.4f) / 2.3f)
        diamond(canvas, cx, cy, r, withAlpha(grey, (110 * appear).toInt()))
        ringSegments(canvas, cx, cy, r * 0.62f, withAlpha(grey, (200 * appear).toInt()), s * 25f, 4, 50f, r * 0.03f)

        // Barre en parallélogramme, rayures diagonales, remplissage blanc.
        val w = r * 1.4f * appear
        val h = r * 0.30f
        parallelogram(cx, cy, w, h, h * 0.45f)
        stroke.color = grey
        stroke.strokeWidth = r * 0.025f
        canvas.drawPath(path, stroke)
        canvas.save()
        canvas.clipPath(path)
        stroke.strokeWidth = r * 0.03f
        stroke.color = withAlpha(grey, 160)
        var x = cx - w
        while (x < cx + w) { canvas.drawLine(x, cy + h, x + h * 1.2f, cy - h, stroke); x += r * 0.09f }
        fill.color = withAlpha(Color.WHITE, 235)
        canvas.drawRect(cx - w, cy - h, cx - w + 2 * w * progress, cy + h, fill)
        canvas.restore()

        // Pastille hexagonale rouge avec le compteur.
        hexagon(cx, cy, h * 0.75f, 90f)
        fill.color = black
        canvas.drawPath(path, fill)
        stroke.color = red
        stroke.strokeWidth = r * 0.03f
        canvas.drawPath(path, stroke)
        text.color = Color.WHITE
        text.textSize = h * 0.55f
        canvas.drawText("${(progress * 100).toInt()}", cx, cy + text.textSize * 0.35f, text)
    }

    // 3 – 5,5 s : anneaux et cœur blanc en trèfle hexagonal.
    private fun whiteCore(canvas: Canvas, cx: Float, cy: Float, r: Float, s: Float) {
        val p = ease((s - 3f) / 0.8f)
        diamond(canvas, cx, cy, r, withAlpha(grey, 110))
        stroke.color = withAlpha(Color.WHITE, (220 * p).toInt())
        stroke.strokeWidth = r * 0.05f
        circleArc(canvas, cx, cy, r * 0.62f, -60f + s * 20f, 300f * p)
        stroke.strokeWidth = r * 0.015f
        circleArc(canvas, cx, cy, r * 0.75f, 0f, 360f * p)
        ringSegments(canvas, cx, cy, r * 0.88f, withAlpha(grey, 200), -s * 30f, 3, 70f, r * 0.02f)
        trefoil(canvas, cx, cy, r * 0.42f * p, s * 35f, Color.WHITE, black)
    }

    // 5,5 – 8,5 s : balayage rouge puis cœur rouge qui pulse, bras mécaniques.
    private fun redCore(canvas: Canvas, cx: Float, cy: Float, r: Float, s: Float) {
        val wipe = ease((s - 5.5f) / 0.7f)
        val pulse = 0.75f + 0.25f * sin((s - 5.5f) * 7f)
        // Halo rouge.
        glow.color = withAlpha(red, (110 * wipe * pulse).toInt())
        canvas.drawCircle(cx, cy, r * 0.55f, glow)
        // Bras mécaniques en diagonale.
        stroke.color = withAlpha(grey, (120 * wipe).toInt())
        stroke.strokeWidth = r * 0.07f
        for (a in listOf(210f, 330f, 90f)) {
            val rad = Math.toRadians(a.toDouble())
            canvas.drawLine(
                cx + cos(rad).toFloat() * r * 0.45f, cy + sin(rad).toFloat() * r * 0.45f,
                cx + cos(rad).toFloat() * r * 0.95f, cy + sin(rad).toFloat() * r * 0.95f, stroke,
            )
        }
        // Crochets latéraux rouges.
        stroke.color = withAlpha(red, (230 * wipe).toInt())
        stroke.strokeWidth = r * 0.025f
        circleArc(canvas, cx, cy, r * 1.05f, 160f, 40f)
        circleArc(canvas, cx, cy, r * 1.05f, -20f, 40f)
        stroke.strokeWidth = r * 0.04f
        canvas.drawLine(cx - r * 0.25f, cy + r * 0.62f, cx + r * 0.25f, cy + r * 0.62f, stroke)
        canvas.drawLine(cx - r * 0.25f, cy - r * 0.62f, cx + r * 0.25f, cy - r * 0.62f, stroke)
        // Cœur : blanc qui vire au rouge.
        trefoil(canvas, cx, cy, r * 0.42f * (0.95f + 0.05f * pulse), s * 15f, blend(Color.WHITE, red, wipe), black)
        // Ligne de balayage qui traverse l'écran.
        if (wipe < 1f) {
            val y = height * wipe
            stroke.color = withAlpha(red, 200)
            stroke.strokeWidth = 4f
            canvas.drawLine(0f, y, width.toFloat(), y, stroke)
        }
    }

    // 8,5 – 11,5 s : panneau bleu d'initialisation.
    private fun initPanel(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val local = s - 8.5f
        val fadeOut = 1f - ease((local - 2.4f) / 0.6f)
        val w = width * 0.92f
        val left = cx - w / 2
        val right = cx + w / 2
        val top = cy - width * 0.07f
        val bottom = cy + width * 0.07f
        val c = withAlpha(blue, (255 * fadeOut).toInt())

        // Rayon lumineux horizontal (reflet d'objectif).
        val flareX = left + w * ease(local / 0.5f)
        fill.shader = LinearGradient(left, 0f, flareX, 0f, Color.TRANSPARENT, withAlpha(Color.WHITE, (200 * fadeOut).toInt()), Shader.TileMode.CLAMP)
        canvas.drawRect(left, cy - 3f, flareX, cy + 3f, fill)
        fill.shader = null
        glow.color = withAlpha(blue, (160 * fadeOut).toInt())
        canvas.drawCircle(left + w * 0.02f, cy, width * 0.06f, glow)

        // Cadre du panneau à angles coupés.
        stroke.color = c
        stroke.strokeWidth = 3f
        path.reset()
        path.moveTo(left + w * 0.12f, top)
        path.lineTo(right - w * 0.18f, top)
        path.lineTo(right - w * 0.12f, top - width * 0.04f)
        path.lineTo(right, top - width * 0.04f)
        canvas.drawPath(path, stroke)
        path.reset()
        path.moveTo(left, bottom)
        path.lineTo(left + w * 0.22f, bottom)
        path.lineTo(left + w * 0.30f, bottom + width * 0.04f)
        path.lineTo(right - w * 0.25f, bottom + width * 0.04f)
        path.lineTo(right - w * 0.18f, bottom)
        path.lineTo(right, bottom)
        canvas.drawPath(path, stroke)

        val loaded = local >= 2.0f
        text.color = withAlpha(Color.WHITE, (255 * fadeOut).toInt())
        text.textSize = width * 0.042f
        val title = if (loaded) "SYSTÈME 1 CHARGÉ" else "INITIALISATION DU SYSTÈME 1"
        val typed = if (loaded) title.length else (local / 0.8f * title.length).toInt().coerceIn(0, title.length)
        val dots = if (loaded) "" else ".".repeat(((s * 4).toInt() % 5))
        canvas.drawText(title.take(typed) + dots, cx, top - width * 0.015f, text)

        // Blocs de progression.
        val blocks = 14
        val filled = (ease((local - 0.6f) / 1.4f) * blocks).toInt()
        val bw = (w * 0.62f) / blocks
        for (k in 0 until blocks) {
            fill.color = if (k < filled) c else withAlpha(blue, (40 * fadeOut).toInt())
            val x = left + w * 0.04f + k * bw
            canvas.drawRect(x, cy - width * 0.025f, x + bw * 0.8f, cy + width * 0.025f, fill)
        }
        text.textSize = width * 0.026f
        text.color = withAlpha(blue, (220 * fadeOut).toInt())
        if (local > 0.9f) canvas.drawText("MÉMOIRE ÉTENDUE : NE PAS SUPPRIMER", cx, bottom + width * 0.10f, text)
        if (local > 1.4f) canvas.drawText("SOMME DE CONTRÔLE : OK", cx, bottom + width * 0.145f, text)
    }

    // 11,5 – 15 s : cercles cyan qui s'assemblent autour de « J.A.R.V.I.S. ».
    private fun finalRings(canvas: Canvas, cx: Float, cy: Float, r: Float, s: Float) {
        val local = s - 11.5f
        val p1 = ease(local / 0.7f)
        val p2 = ease((local - 0.5f) / 0.7f)
        val p3 = ease((local - 1.0f) / 0.6f)
        glow.color = withAlpha(cyan, (70 * p1).toInt())
        canvas.drawCircle(cx, cy, r * 1.0f, glow)

        stroke.color = cyan
        stroke.strokeWidth = r * 0.035f
        circleArc(canvas, cx, cy, r * 1.0f, -90f, 360f * p1)
        stroke.strokeWidth = r * 0.08f
        stroke.color = withAlpha(cyan, 180)
        for (k in 0 until 3) circleArc(canvas, cx, cy, r * 0.88f, k * 120f + local * 25f, 70f * p2)
        stroke.strokeWidth = r * 0.012f
        stroke.color = withAlpha(cyan, 200)
        circleArc(canvas, cx, cy, r * 0.76f, 0f, 360f * p2)
        circleArc(canvas, cx, cy, r * 0.56f, 0f, 360f * p3)
        // Graduations qui tournent.
        stroke.strokeWidth = r * 0.012f
        for (k in 0 until 60) {
            if (k.toFloat() / 60 > p2) break
            val a = Math.toRadians((k * 6 - local * 15).toDouble())
            val inner = if (k % 5 == 0) 0.64f else 0.68f
            canvas.drawLine(
                cx + cos(a).toFloat() * r * inner, cy + sin(a).toFloat() * r * inner,
                cx + cos(a).toFloat() * r * 0.72f, cy + sin(a).toFloat() * r * 0.72f, stroke,
            )
        }
        text.color = withAlpha(cyan, (255 * p3).toInt())
        text.textSize = r * 0.20f
        canvas.drawText("J.A.R.V.I.S.", cx, cy + text.textSize * 0.35f, text)
    }

    // --- Formes ---------------------------------------------------------

    private fun diamond(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        path.reset()
        path.moveTo(cx - r * 1.35f, cy)
        path.lineTo(cx - r * 0.55f, cy - r * 0.8f)
        path.lineTo(cx + r * 0.55f, cy - r * 0.8f)
        path.lineTo(cx + r * 1.35f, cy)
        path.lineTo(cx + r * 0.55f, cy + r * 0.8f)
        path.lineTo(cx - r * 0.55f, cy + r * 0.8f)
        path.close()
        stroke.color = color
        stroke.strokeWidth = r * 0.012f
        canvas.drawPath(path, stroke)
    }

    private fun parallelogram(cx: Float, cy: Float, w: Float, h: Float, slant: Float) {
        path.reset()
        path.moveTo(cx - w + slant, cy - h)
        path.lineTo(cx + w, cy - h)
        path.lineTo(cx + w - slant, cy + h)
        path.lineTo(cx - w, cy + h)
        path.close()
    }

    private fun trefoil(canvas: Canvas, cx: Float, cy: Float, radius: Float, rotation: Float, color: Int, hole: Int) {
        if (radius <= 1f) return
        hexagon(cx, cy, radius, rotation + 90f)
        stroke.color = color
        stroke.strokeWidth = radius * 0.08f
        canvas.drawPath(path, stroke)
        // Trois segments façon trèfle, entre deux cercles.
        fill.color = color
        for (k in 0 until 3) {
            val start = rotation + k * 120f - 30f
            path.reset()
            arc.set(cx - radius * 0.78f, cy - radius * 0.78f, cx + radius * 0.78f, cy + radius * 0.78f)
            path.arcTo(arc, start, 60f)
            arc.set(cx - radius * 0.30f, cy - radius * 0.30f, cx + radius * 0.30f, cy + radius * 0.30f)
            path.arcTo(arc, start + 60f, -60f)
            path.close()
            canvas.drawPath(path, fill)
        }
        fill.color = hole
        canvas.drawCircle(cx, cy, radius * 0.18f, fill)
    }

    private fun ringSegments(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, rotation: Float, count: Int, sweep: Float, width: Float) {
        stroke.color = color
        stroke.strokeWidth = width
        for (k in 0 until count) circleArc(canvas, cx, cy, radius, rotation + k * 360f / count, sweep)
    }

    private fun circleArc(canvas: Canvas, cx: Float, cy: Float, radius: Float, start: Float, sweep: Float) {
        arc.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(arc, start, sweep, false, stroke)
    }

    private fun hexagon(cx: Float, cy: Float, radius: Float, rotation: Float) {
        path.reset()
        for (k in 0 until 6) {
            val a = Math.toRadians((k * 60 + rotation).toDouble())
            val x = cx + (cos(a) * radius).toFloat()
            val y = cy + (sin(a) * radius).toFloat()
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    private fun ease(x: Float): Float {
        val p = x.coerceIn(0f, 1f)
        return p * p * (3 - 2 * p)
    }

    private fun withAlpha(color: Int, alpha: Int) =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun blend(a: Int, b: Int, p: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * p).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * p).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * p).toInt(),
    )

    companion object {
        // Lancé un peu avant les cercles : la voix met une à deux secondes à être générée.
        private const val GREET_AT_MS = 10_000f
    }
}
