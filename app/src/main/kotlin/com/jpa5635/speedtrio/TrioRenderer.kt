package com.jpa5635.speedtrio

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import kotlin.math.max

/** Ready-to-draw text for the three slots plus the small captions under the left and right values. */
data class TrioContent(
    val left: String,
    val middle: String,
    val right: String,
    val leftLabel: String,
    val rightLabel: String,
)

/** Layout rules. tools/preview.py mirrors these numbers exactly. */
object TrioSpec {
    /** Free space above and below the middle digits, as a fraction of field height. */
    const val MARGIN = 0.02f

    /** Middle digit height as a fraction of the full digit height (field height minus margins): 0.9 x 0.9, i.e. 19 % smaller than full; text size shrinks, so width shrinks with it. */
    const val MID_RATIO = 0.81f

    /** Left/right digit height relative to the full digit height (2/3); unchanged by MID_RATIO. */
    const val SIDE_RATIO = 2f / 3f

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
    const val PAD = 0.06f
    const val PAD_MIN_PX = 6f

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
    private val sideFace = loadFace(context, "avenir-oblique.ttf", "nunitosans-italic.ttf") // captions

    // Left/right values are bold italic too (same face as the middle value, just smaller).
    private val sideValueFace = middleFace
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

        // Middle value: centred in the field.
        paint.color = main
        val midW = measure(middleFace, plan.midSize, plan.mid, n)
        draw(canvas, plan.mid, middleFace, plan.midSize, n, (width - midW) / 2f, h / 2f)

        // Left / right: value with its small caption underneath, the pair centred vertically.
        val sideDigitH = digitBounds(sideValueFace, plan.sideSize, 1f).height().toFloat()
        val labelH = h * TrioSpec.LABEL_RATIO
        val gap = h * TrioSpec.LABEL_GAP
        val top = (h - (sideDigitH + gap + labelH)) / 2f
        val valueCy = top + sideDigitH / 2f
        val labelCy = top + sideDigitH + gap + labelH / 2f
        val labelSize = sizeForDigitHeight(sideFace, labelH)

        val leftW = measure(sideValueFace, plan.sideSize, plan.left, plan.sideSqueeze)
        val rightW = measure(sideValueFace, plan.sideSize, plan.right, plan.sideSqueeze)
        val leftX = pad
        val rightX = width - pad - rightW

        paint.color = main
        draw(canvas, plan.left, sideValueFace, plan.sideSize, plan.sideSqueeze, leftX, valueCy)
        draw(canvas, plan.right, sideValueFace, plan.sideSize, plan.sideSqueeze, rightX, valueCy)

        paint.color = dim
        drawLabel(canvas, v.leftLabel, labelSize, leftX + leftW / 2f, labelCy, pad, width - pad)
        drawLabel(canvas, v.rightLabel, labelSize, rightX + rightW / 2f, labelCy, pad, width - pad)

        if (recordingDot) {
            val r = minOf(h * TrioSpec.DOT_RADIUS, TrioSpec.DOT_MAX_RADIUS_PX)
            val inset = max(h * TrioSpec.DOT_INSET, 3f)
            paint.color = RECORDING_RED
            canvas.drawCircle(inset + r, inset + r, r, paint)
        }
        return bitmap
    }

    /** Caption centred on [centerX], kept inside [minX, maxX]. */
    private fun drawLabel(canvas: Canvas, text: String, size: Float, centerX: Float, centerY: Float, minX: Float, maxX: Float) {
        val w = measure(sideFace, size, text, TrioSpec.NARROW)
        val x = (centerX - w / 2f).coerceIn(minX, max(minX, maxX - w))
        draw(canvas, text, sideFace, size, TrioSpec.NARROW, x, centerY)
    }

    private fun plan(w: Float, h: Float, v: TrioContent): Plan {
        val fullDigitHeight = h * (1f - 2f * TrioSpec.MARGIN)
        val midSize = sizeForDigitHeight(middleFace, fullDigitHeight * TrioSpec.MID_RATIO)
        val sideSize = sizeForDigitHeight(sideValueFace, fullDigitHeight * TrioSpec.SIDE_RATIO)
        val pad = max(h * TrioSpec.PAD, TrioSpec.PAD_MIN_PX)
        val n = TrioSpec.NARROW

        val mid = v.middle
        val left = v.left
        val right = v.right

        val midW = measure(middleFace, midSize, mid, n)
        val avail = max((w - midW) / 2f - 2f * pad, 1f) // edge padding + gap to the middle value
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
        val RECORDING_RED = Color.parseColor("#FF2D2D") // same red as the Insta360 extension's recording dot
        const val REF_SIZE = 200f
    }
}
