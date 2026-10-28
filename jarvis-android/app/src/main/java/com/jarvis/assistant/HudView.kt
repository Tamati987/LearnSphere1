package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.BatteryManager
import android.util.AttributeSet
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Interface holographique de la console : un réacteur central dont les anneaux tournent
 * et changent selon l'état de Jarvis, entouré de cadres et de relevés (heure, date, batterie).
 *  - en attente : rotation lente, cyan pâle, le cœur respire ;
 *  - écoute : ondes qui partent du cœur, au rythme de la voix captée par le micro ;
 *  - analyse : arcs dorés qui tournent vite en sens contraire ;
 *  - réponse : barres d'onde autour du cœur ;
 *  - hors ligne : gris, presque immobile.
 */
class HudView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val cyan = Color.parseColor("#4FD8FF")
    private val deep = Color.parseColor("#0E6E8C")
    private val gold = Color.parseColor("#FFB547")
    private val grey = Color.parseColor("#4A5A62")

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        letterSpacing = 0.15f
    }
    private val mono = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
    private val arc = RectF()
    private val path = Path()
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.FRANCE)
    private val day = SimpleDateFormat("EEE d MMM yyyy", Locale.FRANCE)

    private var state = JarvisBus.State.OFF
    private var stateSince = 0L
    // Niveau de la voix captée, lissé pour que les ondes ne tremblent pas.
    private var level = 0f
    private var battery = -1
    private var charging = false
    private var batteryReadAt = 0L
    private var running = false

    init {
        contentDescription = "Interface de Jarvis. Touchez le réacteur pour parler."
    }

    fun setState(s: JarvisBus.State) {
        if (s != state) { state = s; stateSince = System.currentTimeMillis() }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); running = true; postInvalidateOnAnimation() }
    override fun onDetachedFromWindow() { running = false; super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val now = System.currentTimeMillis()
        val t = (now % 3_600_000L) / 1000f
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2
        val cy = h / 2
        val r = min(w, h) * 0.36f
        val color = when (state) {
            JarvisBus.State.OFF -> grey
            JarvisBus.State.THINKING -> gold
            else -> cyan
        }
        level += (JarvisBus.level - level) * 0.25f
        val since = (now - stateSince) / 1000f

        drawGrid(canvas, w, h)
        drawFrame(canvas, w, h, color)

        // Halo du cœur, plus vif quand Jarvis écoute ou parle.
        val breathe = 0.5f + 0.5f * sin(t * 1.6f)
        val intensity = when (state) {
            JarvisBus.State.OFF -> 0.15f
            JarvisBus.State.WAITING -> 0.35f + 0.15f * breathe
            JarvisBus.State.LISTENING -> 0.55f + 0.45f * level
            JarvisBus.State.THINKING -> 0.5f + 0.2f * sin(t * 8)
            JarvisBus.State.SPEAKING -> 0.6f + 0.3f * speech(t, 0)
        }
        glow.shader = RadialGradient(
            cx, cy, r * 1.25f,
            intArrayOf(withAlpha(color, (170 * intensity).toInt()), withAlpha(color, (40 * intensity).toInt()), Color.TRANSPARENT),
            floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, r * 1.25f, glow)

        val speed = when (state) {
            JarvisBus.State.OFF -> 0.1f
            JarvisBus.State.THINKING -> 4f
            JarvisBus.State.LISTENING -> 1.5f
            else -> 1f
        }

        // Anneau extérieur gradué.
        canvas.save()
        canvas.rotate(t * 6 * speed, cx, cy)
        stroke.color = withAlpha(color, 150)
        for (i in 0 until 120) {
            val a = Math.toRadians(i * 3.0)
            val long = i % 10 == 0
            val inner = r * (if (long) 1.02f else 1.06f)
            stroke.strokeWidth = if (long) 3f else 1.5f
            canvas.drawLine(
                cx + inner * cos(a).toFloat(), cy + inner * sin(a).toFloat(),
                cx + r * 1.1f * cos(a).toFloat(), cy + r * 1.1f * sin(a).toFloat(), stroke,
            )
        }
        canvas.restore()

        // Anneau segmenté, en sens inverse.
        ring(canvas, cx, cy, r * 0.92f, -t * 14 * speed, segments = 12, gap = 8f, width = r * 0.07f, color = color, alpha = 200)
        // Arcs fins de balayage.
        stroke.strokeWidth = 2f
        stroke.color = withAlpha(color, 220)
        arc.set(cx - r * 0.8f, cy - r * 0.8f, cx + r * 0.8f, cy + r * 0.8f)
        canvas.drawArc(arc, t * 40 * speed, 70f, false, stroke)
        canvas.drawArc(arc, t * 40 * speed + 180, 70f, false, stroke)
        ring(canvas, cx, cy, r * 0.68f, t * 22 * speed, segments = 3, gap = 30f, width = 4f, color = color, alpha = 180)

        when (state) {
            JarvisBus.State.LISTENING -> {
                // Ondes qui partent du cœur, plus fortes quand la voix est forte.
                for (k in 0 until 3) {
                    val p = ((t * 0.9f + k / 3f) % 1f)
                    stroke.strokeWidth = 3f
                    stroke.color = withAlpha(cyan, ((1 - p) * (120 + 135 * level)).toInt())
                    canvas.drawCircle(cx, cy, r * (0.4f + p * (0.7f + 0.5f * level)), stroke)
                }
            }
            JarvisBus.State.THINKING -> {
                stroke.strokeWidth = r * 0.04f
                stroke.color = withAlpha(gold, 230)
                arc.set(cx - r * 0.55f, cy - r * 0.55f, cx + r * 0.55f, cy + r * 0.55f)
                for (k in 0 until 3) canvas.drawArc(arc, -t * 260 + k * 120, 50f, false, stroke)
            }
            JarvisBus.State.SPEAKING -> {
                // Barres d'onde autour du cœur.
                stroke.strokeWidth = 4f
                stroke.color = withAlpha(cyan, 230)
                for (i in 0 until 48) {
                    val a = Math.toRadians(i * 7.5)
                    val len = r * (0.06f + 0.22f * speech(t, i))
                    val start = r * 0.5f
                    canvas.drawLine(
                        cx + start * cos(a).toFloat(), cy + start * sin(a).toFloat(),
                        cx + (start + len) * cos(a).toFloat(), cy + (start + len) * sin(a).toFloat(), stroke,
                    )
                }
            }
            else -> Unit
        }

        // Cœur : hexagone et triangle lumineux.
        hexagon(canvas, cx, cy, r * 0.42f, t * 10 * speed, color, 140)
        fill.color = withAlpha(Color.WHITE, (120 + 120 * intensity).toInt().coerceAtMost(255))
        canvas.drawCircle(cx, cy, r * (0.16f + 0.03f * (if (state == JarvisBus.State.LISTENING) level else breathe)), fill)
        stroke.strokeWidth = 3f
        stroke.color = withAlpha(color, 255)
        canvas.drawCircle(cx, cy, r * 0.24f, stroke)

        // Libellé d'état sous le réacteur.
        val label = when (state) {
            JarvisBus.State.OFF -> "HORS LIGNE"
            JarvisBus.State.WAITING -> "EN VEILLE · « HEY JARVIS »"
            JarvisBus.State.LISTENING -> "ÉCOUTE"
            JarvisBus.State.THINKING -> "ANALYSE" + ".".repeat(((since * 3).toInt() % 4))
            JarvisBus.State.SPEAKING -> "RÉPONSE"
        }
        text.textAlign = Paint.Align.CENTER
        text.textSize = r * 0.13f
        text.color = withAlpha(color, 230)
        canvas.drawText(label, cx, cy + r * 1.38f, text)

        drawReadouts(canvas, w, h, color, now)

        if (running) postInvalidateOnAnimation()
    }

    /** Amplitude simulée de la voix, par barre : plusieurs sinus qui se croisent. */
    private fun speech(t: Float, i: Int): Float {
        val v = 0.5f + 0.25f * sin(t * 11 + i * 0.9f) + 0.15f * sin(t * 23 + i * 2.3f) + 0.1f * sin(t * 5 - i * 0.4f)
        return v.coerceIn(0f, 1f)
    }

    private fun ring(
        canvas: Canvas, cx: Float, cy: Float, radius: Float, angle: Float,
        segments: Int, gap: Float, width: Float, color: Int, alpha: Int,
    ) {
        stroke.strokeWidth = width
        stroke.color = withAlpha(color, alpha)
        arc.set(cx - radius, cy - radius, cx + radius, cy + radius)
        val sweep = 360f / segments
        for (k in 0 until segments) canvas.drawArc(arc, angle + k * sweep, sweep - gap, false, stroke)
    }

    private fun hexagon(canvas: Canvas, cx: Float, cy: Float, radius: Float, angle: Float, color: Int, alpha: Int) {
        path.reset()
        for (k in 0..6) {
            val a = Math.toRadians(angle + k * 60.0)
            val x = cx + radius * cos(a).toFloat()
            val y = cy + radius * sin(a).toFloat()
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        stroke.strokeWidth = 2.5f
        stroke.color = withAlpha(color, alpha)
        canvas.drawPath(path, stroke)
    }

    /** Fond quadrillé très discret. */
    private fun drawGrid(canvas: Canvas, w: Float, h: Float) {
        stroke.strokeWidth = 1f
        stroke.color = withAlpha(deep, 40)
        val step = min(w, h) / 12
        var x = 0f
        while (x < w) { canvas.drawLine(x, 0f, x, h, stroke); x += step }
        var y = 0f
        while (y < h) { canvas.drawLine(0f, y, w, y, stroke); y += step }
    }

    /** Coins en équerre, façon viseur. */
    private fun drawFrame(canvas: Canvas, w: Float, h: Float, color: Int) {
        val m = 6f
        val l = min(w, h) * 0.09f
        stroke.strokeWidth = 3f
        stroke.color = withAlpha(color, 200)
        for ((x, y, dx, dy) in listOf(
            listOf(m, m, 1f, 1f), listOf(w - m, m, -1f, 1f), listOf(m, h - m, 1f, -1f), listOf(w - m, h - m, -1f, -1f),
        )) {
            canvas.drawLine(x, y, x + l * dx, y, stroke)
            canvas.drawLine(x, y, x, y + l * dy, stroke)
        }
    }

    /** Heure et date en haut à gauche, batterie en haut à droite. */
    private fun drawReadouts(canvas: Canvas, w: Float, h: Float, color: Int, now: Long) {
        if (now - batteryReadAt > 30_000) readBattery(now)
        val size = min(w, h) * 0.045f
        mono.textSize = size
        mono.color = withAlpha(color, 220)
        mono.textAlign = Paint.Align.LEFT
        canvas.drawText(clock.format(Date(now)), size, size * 2, mono)
        mono.color = withAlpha(color, 140)
        canvas.drawText(day.format(Date(now)).uppercase(Locale.FRANCE), size, size * 3.3f, mono)

        mono.textAlign = Paint.Align.RIGHT
        mono.color = withAlpha(color, 220)
        val pct = if (battery >= 0) "$battery %" else "-- %"
        canvas.drawText("ÉNERGIE $pct${if (charging) " · CHARGE" else ""}", w - size, size * 2, mono)
        // Jauge de batterie.
        val gw = size * 7
        val gx = w - size - gw
        val gy = size * 2.8f
        stroke.strokeWidth = 1.5f
        stroke.color = withAlpha(color, 160)
        canvas.drawRect(gx, gy, gx + gw, gy + size * 0.5f, stroke)
        fill.color = withAlpha(if (battery in 0..15) Color.parseColor("#FF4D5E") else color, 200)
        canvas.drawRect(gx, gy, gx + gw * (battery.coerceAtLeast(0) / 100f), gy + size * 0.5f, fill)

        mono.textAlign = Paint.Align.LEFT
        mono.color = withAlpha(color, 110)
        canvas.drawText("J.A.R.V.I.S. · SYS ${if (state == JarvisBus.State.OFF) "OFF" else "OK"}", size, h - size, mono)
    }

    private fun readBattery(now: Long) {
        batteryReadAt = now
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val lvl = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        battery = if (lvl >= 0 && scale > 0) lvl * 100 / scale else -1
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
}
