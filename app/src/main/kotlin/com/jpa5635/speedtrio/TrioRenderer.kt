package com.jpa5635.speedtrio

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Small orange symbol drawn before a caption. */
enum class TrioIcon { SPEEDOMETER, BOLT, HEART, CHAINRING, CRANK }

/** Ready-to-draw text for the three slots plus the small captions (and optional icons) under each value. */
data class TrioContent(
    val left: String,
    val middle: String,
    val right: String,
    val leftLabel: String,
    val rightLabel: String,
    val middleLabel: String = "",
    val leftIcon: TrioIcon? = null,
    val middleIcon: TrioIcon? = null,
    val rightIcon: TrioIcon? = null,
)

/** Layout rules. tools/preview.py mirrors these numbers exactly. */
object TrioSpec {
    /** Free space above and below the middle digits, as a fraction of field height. */
    const val MARGIN = 0.02f

    /**
     * Free space above the middle digits and below the middle caption (fraction of field height). The middle digit
     * height is whatever is left once its caption (same size as the side captions) and the gap above it are taken
     * out: 1 - 2 x MID_PAIR_MARGIN - LABEL_GAP - LABEL_RATIO of the field height (70 % with the values below).
     */
    const val MID_PAIR_MARGIN = 0.06f

    /**
     * Left/right digit height relative to the full digit height: 2/3 x 1.1 (10 % larger than before). Never taller
     * than the middle digits; on normal field heights the width available is what limits them.
     */
    const val SIDE_RATIO = 2f / 3f * 1.1f

    /** The middle digits are this fraction of the height left over after the caption row and margins (10 % smaller). */
    const val MID_SCALE = 0.9f

    /** Caption icon box size relative to the caption height, and the gap between icon and caption text (fraction of field height). */
    const val ICON_RATIO = 1.25f
    const val ICON_GAP = 0.04f

    /** All text is horizontally condensed to this fraction of its natural width (20 % narrower). */
    const val NARROW = 0.8f

    /** Small unit captions under the left/right values: height as a fraction of field height, and gap above them. */
    const val LABEL_RATIO = 0.13f
    const val LABEL_GAP = 0.05f

    /** Recording dot (upper-left corner): radius (fraction of field height, capped in px) and inset from the corner. */
    const val DOT_RADIUS = 0.06f
    const val DOT_MAX_RADIUS_PX = 9f
    const val DOT_INSET = 0.04f

    /** Edge padding and minimum gap between values, as a fraction of height (with a floor in px). */
    const val PAD = 0.05f
    const val PAD_MIN_PX = 6f

    /** Slanted separator lines between the three values (experimental; set false to remove them). */
    const val SEPARATORS = true

    /** Separator length as a fraction of field height, centred top to bottom. */
    const val SEP_HEIGHT = 0.5f

    /** Lean of the separator in degrees; matches the italic angle of the bundled Nunito Sans (-9 deg). */
    const val SEP_ANGLE_DEG = 9f

    /** Separator stroke: fraction of field height, with a floor in px. */
    const val SEP_STROKE = 0.022f
    const val SEP_STROKE_MIN_PX = 2f

    /** Clear space reserved between a side value and the middle value for the separator (fraction of height, floor in px). */
    const val SEP_GAP = 0.15f
    const val SEP_GAP_MIN_PX = 12f

    /**
     * When the left/right values do not fit the width next to the middle value, their size shrinks
     * (never below this fraction of the 2/3 height), then they are squeezed. The middle value never shrinks.
     */
    const val SIDE_MIN_SCALE = 0.45f
}

object TrioTheme {
    /** Set to true / false to force light-on-dark or dark-on-light text; null follows the system. */
    private val FORCE_DARK: Boolean? = null

    fun isDark(context: Context): Boolean = FORCE_DARK
        ?: ((context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
}

class TrioRenderer(context: Context) {
    // Drop licensed Avenir files into app/src/main/assets/fonts/ with these names and they are used
    // automatically; otherwise the bundled Nunito Sans (SIL OFL, an Avenir-style face) is used.
    private val middleFace = loadFace(context, "avenir-bold-oblique.ttf", "nunitosans-bolditalic.ttf")

    // Left/right values and the captions are bold italic too (same face as the middle value).
    private val sideValueFace = middleFace
    private val labelFace = middleFace
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)

    private class Plan(val mid: String, val midSize: Float, val left: String, val right: String, val sideSize: Float, val sideSqueeze: Float)

    fun render(width: Int, height: Int, v: TrioContent, dark: Boolean, recordingDot: Boolean = false): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val main = if (dark) Color.WHITE else Color.parseColor("#111111")
        val dim = if (dark) Color.parseColor("#B8BEC6") else Color.parseColor("#4A5058")

        val h = height.toFloat()
        val plan = plan(width.toFloat(), h, v)
        val pad = max(h * TrioSpec.PAD, TrioSpec.PAD_MIN_PX)
        val n = TrioSpec.NARROW

        // Rows shared by all three values: the middle value + its caption are centred vertically (with
        // MID_PAIR_MARGIN above and below); the side values sit on the same baseline with their captions on
        // the same caption row.
        val labelH = h * TrioSpec.LABEL_RATIO
        val gap = h * TrioSpec.LABEL_GAP
        val midDigitH = midDigitHeight(h)
        val midTop = (h - (midDigitH + gap + labelH)) / 2f
        val baseline = midTop + midDigitH
        val midCy = midTop + midDigitH / 2f
        val labelCy = baseline + gap + labelH / 2f
        val labelSize = sizeForDigitHeight(labelFace, labelH)
        val iconSize = labelH * TrioSpec.ICON_RATIO
        val iconGap = h * TrioSpec.ICON_GAP

        // Middle value, centred horizontally in the field, with its caption underneath.
        paint.color = main
        val midW = measure(middleFace, plan.midSize, plan.mid, n)
        draw(canvas, plan.mid, middleFace, plan.midSize, n, (width - midW) / 2f, midCy)

        // Left / right values: same baseline as the middle value.
        val sideDigitH = digitBounds(sideValueFace, plan.sideSize, 1f).height().toFloat()
        val valueCy = baseline - sideDigitH / 2f

        val leftW = measure(sideValueFace, plan.sideSize, plan.left, plan.sideSqueeze)
        val rightW = measure(sideValueFace, plan.sideSize, plan.right, plan.sideSqueeze)

        // Each side value is centred in the space between the field edge and its separator. The separator
        // sits in the middle of the gap next to the middle value.
        val midLeft = (width - midW) / 2f
        val midRight = midLeft + midW
        val gapToMid = gapToMid(h, pad)
        val sepLeft = midLeft - gapToMid / 2f
        val sepRight = midRight + gapToMid / 2f
        val leftX = (sepLeft / 2f - leftW / 2f).coerceAtLeast(pad)
        val rightX = ((sepRight + width) / 2f - rightW / 2f).coerceAtMost(width - pad - rightW)

        paint.color = main
        draw(canvas, plan.left, sideValueFace, plan.sideSize, plan.sideSqueeze, leftX, valueCy)
        draw(canvas, plan.right, sideValueFace, plan.sideSize, plan.sideSqueeze, rightX, valueCy)

        if (TrioSpec.SEPARATORS) {
            paint.color = SEPARATOR_GRAY
            drawSeparator(canvas, sepLeft, h)
            drawSeparator(canvas, sepRight, h)
        }

        paint.color = dim
        if (v.middleLabel.isNotEmpty()) {
            drawLabel(canvas, v.middleLabel, v.middleIcon, labelSize, iconSize, iconGap, width / 2f, labelCy, pad, width - pad)
        }
        drawLabel(canvas, v.leftLabel, v.leftIcon, labelSize, iconSize, iconGap, leftX + leftW / 2f, labelCy, pad, width - pad)
        drawLabel(canvas, v.rightLabel, v.rightIcon, labelSize, iconSize, iconGap, rightX + rightW / 2f, labelCy, pad, width - pad)

        if (recordingDot) {
            val r = minOf(h * TrioSpec.DOT_RADIUS, TrioSpec.DOT_MAX_RADIUS_PX)
            val inset = max(h * TrioSpec.DOT_INSET, 3f)
            paint.color = RECORDING_RED
            canvas.drawCircle(inset + r, inset + r, r, paint)
        }
        return bitmap
    }

    /** Slanted line (leaning like the italics) centred at [centerX], vertically centred, SEP_HEIGHT of the field tall. */
    private fun drawSeparator(canvas: Canvas, centerX: Float, h: Float) {
        val len = h * TrioSpec.SEP_HEIGHT
        val dx = (len / 2f) * Math.tan(Math.toRadians(TrioSpec.SEP_ANGLE_DEG.toDouble())).toFloat()
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = max(h * TrioSpec.SEP_STROKE, TrioSpec.SEP_STROKE_MIN_PX)
        // italics lean right: the top end is further right than the bottom end
        canvas.drawLine(centerX + dx, (h - len) / 2f, centerX - dx, (h + len) / 2f, paint)
        paint.style = Paint.Style.FILL
    }

    /** Icon (if any) followed by the caption, centred as a group on [centerX] and kept inside [minX, maxX]. */
    private fun drawLabel(
        canvas: Canvas, text: String, icon: TrioIcon?, size: Float, iconSize: Float, iconGap: Float,
        centerX: Float, centerY: Float, minX: Float, maxX: Float,
    ) {
        val textW = measure(labelFace, size, text, TrioSpec.NARROW)
        val iconW = if (icon != null) iconSize + iconGap else 0f
        val groupW = iconW + textW
        val x = (centerX - groupW / 2f).coerceIn(minX, max(minX, maxX - groupW))
        if (icon != null) {
            val saved = paint.color
            TrioIcons.draw(canvas, paint, icon, x + iconSize / 2f, centerY, iconSize)
            paint.color = saved
        }
        draw(canvas, text, labelFace, size, TrioSpec.NARROW, x + iconW, centerY)
    }

    /** Middle digit height: field height minus the margins, the gap above the caption and the caption itself, times MID_SCALE. */
    private fun midDigitHeight(h: Float): Float =
        TrioSpec.MID_SCALE * h * (1f - 2f * TrioSpec.MID_PAIR_MARGIN - TrioSpec.LABEL_GAP - TrioSpec.LABEL_RATIO)

    /** Space between a side value's zone and the middle value; the separator is drawn in its middle. */
    private fun gapToMid(h: Float, pad: Float): Float =
        if (TrioSpec.SEPARATORS) max(h * TrioSpec.SEP_GAP, TrioSpec.SEP_GAP_MIN_PX) else pad

    private fun plan(w: Float, h: Float, v: TrioContent): Plan {
        val fullDigitHeight = h * (1f - 2f * TrioSpec.MARGIN)
        val midSize = sizeForDigitHeight(middleFace, midDigitHeight(h))
        val sideSize = sizeForDigitHeight(sideValueFace, minOf(fullDigitHeight * TrioSpec.SIDE_RATIO, midDigitHeight(h)))
        val pad = max(h * TrioSpec.PAD, TrioSpec.PAD_MIN_PX)
        val n = TrioSpec.NARROW

        val mid = v.middle
        val left = v.left
        val right = v.right

        val midW = measure(middleFace, midSize, mid, n)
        // edge padding + gap to the middle value (room for the separator when enabled)
        val avail = max((w - midW) / 2f - pad - gapToMid(h, pad), 1f)
        val widest = max(measure(sideValueFace, sideSize, left, n), measure(sideValueFace, sideSize, right, n))
        val scale = avail / widest
        return when {
            scale >= 1f -> Plan(mid, midSize, left, right, sideSize, n)
            scale >= TrioSpec.SIDE_MIN_SCALE -> Plan(mid, midSize, left, right, sideSize * scale, n)
            // Very short/narrow field: smallest size, squeezed horizontally to fit.
            else -> Plan(mid, midSize, left, right, sideSize * TrioSpec.SIDE_MIN_SCALE, n * scale / TrioSpec.SIDE_MIN_SCALE)
        }
    }

    /** Text size at which the digits 0-9 are exactly [target] px tall (works for any font). */
    private fun sizeForDigitHeight(face: Typeface, target: Float): Float {
        val ref = REF_SIZE
        val r = digitBounds(face, ref, 1f)
        return ref * target / r.height()
    }

    private fun digitBounds(face: Typeface, size: Float, squeeze: Float): Rect {
        paint.typeface = face
        paint.textSize = size
        paint.textScaleX = squeeze
        val r = Rect()
        paint.getTextBounds(DIGITS, 0, DIGITS.length, r)
        return r
    }

    private fun measure(face: Typeface, size: Float, text: String, squeeze: Float): Float {
        paint.typeface = face
        paint.textSize = size
        paint.textScaleX = squeeze
        return paint.measureText(text)
    }

    /** Draws [text] with its digits vertically centred on [centerY]. */
    private fun draw(canvas: Canvas, text: String, face: Typeface, size: Float, squeeze: Float, x: Float, centerY: Float) {
        val r = digitBounds(face, size, squeeze)
        val baseline = centerY - (r.top + r.bottom) / 2f
        paint.typeface = face
        paint.textSize = size
        paint.textScaleX = squeeze
        canvas.drawText(text, x, baseline, paint)
    }

    private fun loadFace(context: Context, preferred: String, fallback: String): Typeface {
        for (name in listOf(preferred, fallback)) {
            try {
                return Typeface.createFromAsset(context.assets, "fonts/$name")
            } catch (_: RuntimeException) {
                // file not present, try next
            }
        }
        return Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)
    }

    private companion object {
        const val DIGITS = "0123456789"
        val SEPARATOR_GRAY = Color.parseColor("#858B93") // medium gray, reads on both the black and white themes
        val RECORDING_RED = Color.parseColor("#FF2D2D") // same red as the Insta360 extension's recording dot
        const val REF_SIZE = 200f
    }
}

/**
 * Caption icons, all Oklahoma State orange, drawn from simple shapes in a 24 x 24 box scaled to the requested size.
 * tools/preview.py draws the same shapes with the same numbers.
 */
private object TrioIcons {
    private val ICON_ORANGE = Color.parseColor("#FE5C00") // Oklahoma State orange

    fun draw(canvas: Canvas, paint: Paint, icon: TrioIcon, cx: Float, cy: Float, size: Float) {
        val s = size / 24f
        val ox = cx - size / 2f
        val oy = cy - size / 2f
        fun x(v: Float) = ox + v * s
        fun y(v: Float) = oy + v * s

        paint.color = ICON_ORANGE

        fun dot(a: Float, b: Float, r: Float) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(x(a), y(b), r * s, paint)
        }

        fun line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, cap: Paint.Cap) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = w * s
            paint.strokeCap = cap
            canvas.drawLine(x(x1), y(y1), x(x2), y(y2), paint)
        }

        fun ring(a: Float, b: Float, r: Float, w: Float) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = w * s
            canvas.drawCircle(x(a), y(b), r * s, paint)
        }

        fun polygon(vararg pts: Pair<Float, Float>) {
            val path = Path()
            pts.forEachIndexed { i, (a, b) -> if (i == 0) path.moveTo(x(a), y(b)) else path.lineTo(x(a), y(b)) }
            path.close()
            paint.style = Paint.Style.FILL
            canvas.drawPath(path, paint)
        }

        fun polarX(r: Float, deg: Float) = 12f + r * cos(Math.toRadians(deg.toDouble())).toFloat()
        fun polarY(r: Float, deg: Float) = 12f + r * sin(Math.toRadians(deg.toDouble())).toFloat()

        when (icon) {
            TrioIcon.SPEEDOMETER -> {
                // three-quarter dial open at the bottom, needle, hub
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.2f * s
                paint.strokeCap = Paint.Cap.ROUND
                canvas.drawArc(RectF(x(3.5f), y(5f), x(20.5f), y(22f)), 135f, 270f, false, paint)
                line(12f, 13.5f, 17.4f, 8.1f, 2.2f, Paint.Cap.ROUND)
                dot(12f, 13.5f, 2.1f)
            }
            TrioIcon.BOLT -> polygon(13f to 2f, 4.5f to 13.5f, 11f to 13.5f, 10f to 22f, 19.5f to 9.5f, 13f to 9.5f)
            TrioIcon.HEART -> {
                dot(7.5f, 8.2f, 5.4f)
                dot(16.5f, 8.2f, 5.4f)
                polygon(2.2f to 9.6f, 21.8f to 9.6f, 12f to 21.5f)
            }
            TrioIcon.CHAINRING -> {
                // ring, ten teeth, five spider arms, hub
                ring(12f, 12f, 6f, 2f)
                for (i in 0 until 10) {
                    val deg = i * 36f
                    line(polarX(6f, deg), polarY(6f, deg), polarX(9.4f, deg), polarY(9.4f, deg), 2.8f, Paint.Cap.BUTT)
                }
                for (i in 0 until 5) {
                    val deg = -90f + 72f * i
                    line(polarX(2f, deg), polarY(2f, deg), polarX(6f, deg), polarY(6f, deg), 1.5f, Paint.Cap.BUTT)
                }
                dot(12f, 12f, 2f)
            }
            TrioIcon.CRANK -> {
                // hollow axle hub, tapered crank arm, pedal bar across its end
                ring(7f, 7f, 2.9f, 2.6f)
                polygon(10.96f to 7.56f, 18.06f to 15.94f, 15.94f to 18.06f, 7.56f to 10.96f)
                line(14.17f, 19.83f, 19.83f, 14.17f, 3.2f, Paint.Cap.BUTT)
            }
        }
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT
    }
}
