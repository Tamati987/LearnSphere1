package com.jarvis.assistant

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Écran d'accueil de Jarvis : l'image J.A.R.V.I.S, animée selon ce que fait Jarvis
 * (le réacteur respire en attente, s'illumine et vibre quand il parle, des arcs tournent
 * quand il réfléchit, le micro brille en vert quand il écoute, les ondes sonores bougent
 * avec sa voix), et ses boutons rendus cliquables.
 */
class HomeView(context: Context) : View(context) {

    /** Zones de l'image qui réagissent au toucher. */
    enum class Spot { SETTINGS, CONTROL_PHONE, OPEN_APP, QUESTIONS, AGENDA, MICROPHONE, HOME, FUNCTIONS, TOOLS, PARAMETERS }

    var onSpot: (Spot) -> Unit = {}

    var state: JarvisBus.State = JarvisBus.State.WAITING
        set(value) { field = value; invalidate() }

    /** Dernière phrase de Jarvis, affichée sous l'état. */
    var caption: String = ""
        set(value) { field = value; invalidate() }

    private val image: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.jarvis_home)
    private val dest = RectF()
    private var scale = 1f
    private val start = System.nanoTime()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    // Coordonnées dans l'image (941 × 1614 pixels).
    private val spots = listOf(
        Spot.SETTINGS to RectF(840f, 45f, 925f, 120f),
        Spot.CONTROL_PHONE to RectF(25f, 232f, 218f, 404f),
        Spot.OPEN_APP to RectF(722f, 232f, 917f, 404f),
        Spot.QUESTIONS to RectF(25f, 434f, 218f, 602f),
        Spot.AGENDA to RectF(722f, 434f, 917f, 602f),
        Spot.MICROPHONE to RectF(345f, 982f, 595f, 1232f),
        Spot.HOME to RectF(40f, 1442f, 175f, 1582f),
        Spot.FUNCTIONS to RectF(215f, 1442f, 350f, 1582f),
        Spot.MICROPHONE to RectF(395f, 1430f, 545f, 1590f),
        Spot.TOOLS to RectF(595f, 1442f, 730f, 1582f),
        Spot.PARAMETERS to RectF(760f, 1442f, 915f, 1582f),
    )

    init {
        setBackgroundColor(Color.BLACK)
        isClickable = true
        contentDescription = "Accueil de Jarvis"
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // L'image occupe toute la largeur, centrée en hauteur, sans déformation.
        scale = w / IMAGE_W
        val height = IMAGE_H * scale
        val top = ((h - height) / 2f).coerceAtLeast(0f)
        dest.set(0f, top, w.toFloat(), top + height)
    }

    private fun x(v: Float) = dest.left + v * scale
    private fun y(v: Float) = dest.top + v * scale
    private fun s(v: Float) = v * scale

    override fun onDraw(canvas: Canvas) {
        canvas.drawBitmap(image, null, dest, paint)
        val t = (System.nanoTime() - start) / 1e9f
        when (state) {
            JarvisBus.State.SPEAKING -> drawSpeaking(canvas, t)
            JarvisBus.State.THINKING -> drawThinking(canvas, t)
            JarvisBus.State.LISTENING -> drawListening(canvas, t)
            JarvisBus.State.WAITING -> drawIdle(canvas, t)
            JarvisBus.State.OFF -> Unit
        }
        drawStatus(canvas)
        drawClock(canvas)
        // Animation continue tant que l'écran est affiché (et figée quand Jarvis est en pause).
        if (state != JarvisBus.State.OFF && isShown) postInvalidateOnAnimation()
    }

    /** Halo lumineux, du centre vers l'extérieur. */
    private fun glow(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, alpha: Float) {
        if (radius <= 0f) return
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        paint.shader = RadialGradient(
            x(cx), y(cy), s(radius),
            intArrayOf(Color.argb(a, Color.red(color), Color.green(color), Color.blue(color)), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(x(cx), y(cy), s(radius), paint)
        paint.shader = null
    }

    private fun drawIdle(canvas: Canvas, t: Float) {
        // Le réacteur respire doucement.
        val breath = 0.5f + 0.5f * sin(t * 2f * PI.toFloat() / 3.5f)
        glow(canvas, REACTOR_X, REACTOR_Y, 150f, CYAN, 0.18f + 0.17f * breath)
        orbitDot(canvas, t * 0.25f, 0.6f)
    }

    private fun drawListening(canvas: Canvas, t: Float) {
        val pulse = 0.5f + 0.5f * sin(t * 2f * PI.toFloat() * 1.4f)
        glow(canvas, REACTOR_X, REACTOR_Y, 150f, CYAN, 0.3f)
        // Le micro brille en vert, avec un anneau qui s'élargit.
        glow(canvas, MIC_X, MIC_Y, 150f + 25f * pulse, GREEN, 0.35f + 0.25f * pulse)
        stroke.color = GREEN
        stroke.alpha = ((1f - pulse) * 200).toInt()
        stroke.strokeWidth = s(4f)
        canvas.drawCircle(x(MIC_X), y(MIC_Y), s(125f + 45f * pulse), stroke)
        waves(canvas, t, 0.45f, GREEN)
    }

    private fun drawThinking(canvas: Canvas, t: Float) {
        glow(canvas, REACTOR_X, REACTOR_Y, 170f, CYAN, 0.35f)
        // Deux arcs tournent autour du réacteur, en sens contraires.
        stroke.color = CYAN
        stroke.alpha = 230
        stroke.strokeWidth = s(6f)
        val outer = RectF(x(REACTOR_X - 185f), y(REACTOR_Y - 185f), x(REACTOR_X + 185f), y(REACTOR_Y + 185f))
        canvas.drawArc(outer, t * 220f, 80f, false, stroke)
        canvas.drawArc(outer, t * 220f + 180f, 80f, false, stroke)
        stroke.color = Color.WHITE
        stroke.strokeWidth = s(3f)
        val inner = RectF(x(REACTOR_X - 120f), y(REACTOR_Y - 120f), x(REACTOR_X + 120f), y(REACTOR_Y + 120f))
        canvas.drawArc(inner, -t * 300f, 50f, false, stroke)
        orbitDot(canvas, t * 1.2f, 1f)
    }

    private fun drawSpeaking(canvas: Canvas, t: Float) {
        // Intensité de la « voix » : plusieurs rythmes mêlés, comme une syllabe après l'autre.
        val voice = (0.55f + 0.25f * sin(t * 17f) + 0.2f * sin(t * 7.3f + 1f)).coerceIn(0.2f, 1f)
        glow(canvas, REACTOR_X, REACTOR_Y, 160f + 70f * voice, CYAN, 0.45f + 0.4f * voice)
        glow(canvas, REACTOR_X, REACTOR_Y, 70f, Color.WHITE, 0.35f + 0.5f * voice)
        // Le globe s'éclaire et un anneau d'onde part du réacteur.
        glow(canvas, GLOBE_X, GLOBE_Y, 260f, BLUE, 0.12f + 0.15f * voice)
        val ring = (t * 0.9f) % 1f
        stroke.color = CYAN
        stroke.alpha = ((1f - ring) * 180).toInt()
        stroke.strokeWidth = s(3f)
        canvas.drawCircle(x(REACTOR_X), y(REACTOR_Y), s(170f + 140f * ring), stroke)
        glow(canvas, MIC_X, MIC_Y, 150f, CYAN, 0.2f + 0.3f * voice)
        waves(canvas, t, 0.6f + 0.4f * voice, CYAN)
        orbitDot(canvas, t * 0.8f, 1f)
    }

    /** Barres des ondes sonores, de chaque côté du micro, qui bougent avec la voix. */
    private fun waves(canvas: Canvas, t: Float, level: Float, color: Int) {
        paint.color = color
        val count = 22
        for (side in listOf(-1, 1)) {
            for (i in 0 until count) {
                // Plus haute près du micro, plus basse vers les bords.
                val distance = i.toFloat() / count
                val bx = MIC_X + side * (150f + i * 12.5f)
                val wobble = abs(sin(t * (9f + i * 0.7f) + i * 1.3f + side))
                val height = (8f + 70f * (1f - distance) * wobble) * level
                paint.alpha = (140 + 115 * (1f - distance)).toInt().coerceAtMost(255)
                canvas.drawRoundRect(
                    RectF(x(bx - 2.5f), y(WAVES_Y - height), x(bx + 2.5f), y(WAVES_Y + height)),
                    s(2.5f), s(2.5f), paint,
                )
            }
        }
        paint.alpha = 255
    }

    /** Point lumineux qui fait le tour de l'anneau du globe. */
    private fun orbitDot(canvas: Canvas, turns: Float, strength: Float) {
        val angle = turns * 2f * PI.toFloat()
        val ox = ORBIT_X + ORBIT_RX * cos(angle)
        val oy = ORBIT_Y + ORBIT_RY * sin(angle)
        glow(canvas, ox, oy, 28f, CYAN, 0.8f * strength)
        glow(canvas, ox, oy, 8f, Color.WHITE, strength)
    }

    private val clockPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.FRANCE)
    private val dateFormat = java.text.SimpleDateFormat("EEEE d MMMM", java.util.Locale.FRANCE)

    /** Panneau central : l'heure et la date, en direct. */
    private fun drawClock(canvas: Canvas) {
        val now = java.util.Date()
        clockPaint.color = Color.rgb(214, 250, 255)
        clockPaint.textSize = s(96f)
        clockPaint.setShadowLayer(s(14f), 0f, 0f, CYAN)
        canvas.drawText(timeFormat.format(now), x(470f), y(852f), clockPaint)
        clockPaint.textSize = s(32f)
        clockPaint.setShadowLayer(0f, 0f, 0f, 0)
        clockPaint.color = Color.rgb(170, 225, 245)
        canvas.drawText(dateFormat.format(now).replaceFirstChar { it.uppercase() }, x(470f), y(905f), clockPaint)
    }

    /** Cadre d'état : l'état réel de Jarvis à la place du texte de l'image. */
    private fun drawStatus(canvas: Canvas) {
        paint.color = STATUS_BG
        canvas.drawRect(x(340f), y(1272f), x(870f), y(1384f), paint)
        val (title, color) = when (state) {
            JarvisBus.State.OFF -> "Jarvis est en pause" to Color.rgb(255, 170, 60)
            JarvisBus.State.WAITING -> "Jarvis est prêt !" to GREEN
            JarvisBus.State.LISTENING -> "Je vous écoute…" to GREEN
            JarvisBus.State.THINKING -> "Analyse en cours…" to CYAN
            JarvisBus.State.SPEAKING -> "Jarvis vous répond…" to CYAN
        }
        val width = s(520f)
        titlePaint.color = color
        titlePaint.textSize = s(40f)
        canvas.drawText(TextUtils.ellipsize(title, titlePaint, width, TextUtils.TruncateAt.END).toString(), x(345f), y(1318f), titlePaint)
        captionPaint.textSize = s(26f)
        // Aucune consigne à l'écran : seulement la dernière phrase de Jarvis.
        val text = if (state == JarvisBus.State.OFF) "" else caption
        canvas.drawText(TextUtils.ellipsize(text, captionPaint, width, TextUtils.TruncateAt.END).toString(), x(345f), y(1362f), captionPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val ix = (event.x - dest.left) / scale
            val iy = (event.y - dest.top) / scale
            spots.firstOrNull { it.second.contains(ix, iy) }?.let { (spot, _) ->
                performClick()
                onSpot(spot)
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    companion object {
        private const val IMAGE_W = 941f
        private const val IMAGE_H = 1614f
        private const val REACTOR_X = 470f
        private const val REACTOR_Y = 355f
        private const val GLOBE_X = 470f
        private const val GLOBE_Y = 700f
        private const val ORBIT_X = 470f
        private const val ORBIT_Y = 687f
        private const val ORBIT_RX = 335f
        private const val ORBIT_RY = 45f
        private const val MIC_X = 470f
        private const val MIC_Y = 1107f
        private const val WAVES_Y = 1127f
        private val CYAN = Color.rgb(53, 231, 255)
        private val BLUE = Color.rgb(22, 119, 255)
        private val GREEN = Color.rgb(92, 255, 107)
        private val STATUS_BG = Color.rgb(0, 5, 12)
    }
}
