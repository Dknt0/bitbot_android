package com.bitbot.ui.screens.plot.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** One drawable curve. [values] is oldest..newest; newest aligns with sampleIdx. */
class PlotSeries(val color: Color, val values: List<Double>)

/** Immutable snapshot of everything needed to draw the plot once. */
class PlotFrame(
    val xEnd: Float,
    val xSpan: Float,
    val yMin: Float,
    val yMax: Float,
    val sampleIdx: Long,
    val series: List<PlotSeries>
)

/**
 * Interactive pan/zoom state for the plot. followX keeps the right edge at the
 * latest sample; autoY fits the y range to the visible data. Any manual x/y
 * interaction disables the corresponding mode until re-enabled.
 */
class PlotViewState {
    var followX by mutableStateOf(true)
    var autoY by mutableStateOf(true)
    /** Step index displayed at the right edge. */
    var xEnd by mutableFloatStateOf(0f)
    /** Visible step span (x zoom). */
    var xSpan by mutableFloatStateOf(100f)
    var yMin by mutableFloatStateOf(0f)
    var yMax by mutableFloatStateOf(1f)

    fun reset() {
        followX = true
        autoY = true
    }
}

private const val MIN_X_SPAN = 5f
private const val MIN_Y_SPAN = 1e-9f

/**
 * Pure renderer on android.graphics.Canvas — used both by the live Compose
 * canvas and by the PNG export, so a saved image matches the screen exactly.
 */
object PlotRenderer {

    private val gridColor = 0xFF3A3A3A.toInt()
    private val axisTextColor = 0xFFBDBDBD.toInt()

    fun textPaint(densityScale: Float): Paint = Paint().apply {
        color = axisTextColor
        textSize = 9f * densityScale
        isAntiAlias = true
    }

    fun render(frame: PlotFrame, canvas: Canvas, widthPx: Float, heightPx: Float, textPaint: Paint) {
        val plotLeft = textPaint.textSize * 3.4f + 4f
        val plotBottom = heightPx - textPaint.textSize * 2f
        val plotW = max(1f, widthPx - plotLeft - 4f)
        val plotH = max(1f, plotBottom - 4f)

        val xEnd = frame.xEnd
        val xSpan = max(frame.xSpan, MIN_X_SPAN)
        val xStart = xEnd - xSpan
        val yMin = frame.yMin
        val yMax = frame.yMax
        val ySpan = max(yMax - yMin, MIN_Y_SPAN)

        fun xToPx(x: Float) = plotLeft + (x - xStart) / xSpan * plotW
        fun stepAtPx(px: Float) = xStart + (px - plotLeft) / plotW * xSpan
        fun yToPx(v: Double): Float {
            val t = ((v - yMin) / ySpan).toFloat().coerceIn(-0.5f, 1.5f)
            return plotBottom - t * plotH
        }

        val gridPaint = Paint().apply { color = gridColor; strokeWidth = 1f; isAntiAlias = true }
        val gridFaint = Paint().apply { color = gridColor; alpha = 100; strokeWidth = 1f }
        val curvePaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true }

        // frame
        canvas.drawLine(plotLeft, plotBottom - plotH, plotLeft, plotBottom, gridPaint)
        canvas.drawLine(plotLeft, plotBottom, plotLeft + plotW, plotBottom, gridPaint)

        // x grid + labels
        for (t in niceTicks(xStart.toDouble(), xEnd.toDouble(), 5)) {
            val px = xToPx(t)
            if (px >= plotLeft && px <= plotLeft + plotW) {
                canvas.drawLine(px, plotBottom - plotH, px, plotBottom, gridFaint)
                val label = formatStep(t)
                canvas.drawText(label, px - textPaint.measureText(label) / 2f, heightPx - textPaint.textSize * 0.4f, textPaint)
            }
        }
        // y grid + labels
        for (t in niceTicks(yMin.toDouble(), yMax.toDouble(), 4)) {
            val py = yToPx(t.toDouble())
            if (py >= 0 && py <= plotBottom) {
                canvas.drawLine(plotLeft, py, plotLeft + plotW, py, gridFaint)
                canvas.drawText(formatValue(t.toDouble()), 2f, py + 3f, textPaint)
            }
        }

        // curves
        val samplesPerPixel = xSpan / plotW
        for (s in frame.series) {
            val n = s.values.size
            if (n == 0) continue
            val base = frame.sampleIdx - n // x of i-th value = base + i
            val iFrom = max(0, ceil(xStart - base).toInt())
            val iTo = min(n - 1, floor(xEnd - base).toInt())
            if (iTo < iFrom) continue

            curvePaint.color = s.color.toArgb()
            val path = android.graphics.Path()
            if (samplesPerPixel <= 1f) {
                for (i in iFrom..iTo) {
                    val px = xToPx((base + i).toFloat())
                    val py = yToPx(s.values[i])
                    if (i == iFrom) path.moveTo(px, py) else path.lineTo(px, py)
                }
                curvePaint.strokeWidth = 2f
            } else {
                // One vertical min/max segment per pixel column
                val firstCol = max(0f, floor(xToPx((base + iFrom).toFloat()) - plotLeft))
                val lastCol = min(plotW, floor(xToPx((base + iTo).toFloat()) - plotLeft))
                var col = firstCol
                while (col <= lastCol) {
                    val px = plotLeft + col
                    val iA = max(iFrom, ceil(stepAtPx(px) - base).toInt())
                    val iB = min(iTo, floor(stepAtPx(px + 1f) - base).toInt())
                    if (iB >= iA) {
                        var lo = s.values[iA]
                        var hi = lo
                        for (i in iA..iB) {
                            val v = s.values[i]
                            if (v < lo) lo = v
                            if (v > hi) hi = v
                        }
                        path.moveTo(px, yToPx(lo))
                        path.lineTo(px, yToPx(hi))
                    }
                    col += 1f
                }
                curvePaint.strokeWidth = 1.5f
            }
            canvas.drawPath(path, curvePaint)
        }
    }

    fun renderToBitmap(frame: PlotFrame, widthPx: Int, heightPx: Int, textScale: Float): Bitmap {
        val bmp = Bitmap.createBitmap(max(1, widthPx), max(1, heightPx), Bitmap.Config.ARGB_8888)
        render(frame, Canvas(bmp), bmp.width.toFloat(), bmp.height.toFloat(), textPaint(textScale))
        return bmp
    }
}

/**
 * Auto-fit y range over the visible samples; null when no visible samples.
 */
internal fun autoFitY(series: List<PlotSeries>, xStart: Float, xEnd: Float, sampleIdx: Long): Pair<Float, Float>? {
    var lo = Double.POSITIVE_INFINITY
    var hi = Double.NEGATIVE_INFINITY
    for (s in series) {
        val n = s.values.size
        if (n == 0) continue
        val base = sampleIdx - n
        val iFrom = max(0, ceil(xStart - base).toInt())
        val iTo = min(n - 1, floor(xEnd - base).toInt())
        for (i in iFrom..iTo) {
            val v = s.values[i]
            if (v.isFinite()) {
                if (v < lo) lo = v
                if (v > hi) hi = v
            }
        }
    }
    if (lo == Double.POSITIVE_INFINITY) return null
    if (abs(hi - lo) < 1e-12) {
        lo -= 1.0; hi += 1.0
    }
    val pad = (hi - lo) * 0.05
    return (lo - pad).toFloat() to (hi + pad).toFloat()
}

/**
 * Realtime line plot on a raw Compose Canvas.
 *
 * - x axis: step index (sample number); y axis: channel values.
 * - follow mode: window = [sampleIdx - horizonSamples, sampleIdx].
 * - drag pans, pinch zooms (x and y independently).
 * - per-pixel min/max decimation when more samples than pixels.
 */
@Composable
fun PlotCanvas(
    state: PlotViewState,
    version: Long,
    sampleIdx: Long,
    horizonSamples: Int,
    seriesProvider: () -> List<PlotSeries>,
    modifier: Modifier = Modifier
) {
    var viewWidth by remember { mutableStateOf(1f) }
    var viewHeight by remember { mutableStateOf(1f) }

    // Follow mode: pin the right edge to the latest sample
    LaunchedEffect(version, state.followX) {
        if (state.followX) {
            state.xEnd = sampleIdx.toFloat()
            state.xSpan = maxOf(horizonSamples.toFloat(), MIN_X_SPAN)
        }
    }

    val density = LocalDensity.current
    val textPaint = remember(density) { PlotRenderer.textPaint(density.density) }

    Canvas(
        modifier = modifier
            .onSizeChanged { size ->
                viewWidth = size.width.toFloat()
                viewHeight = size.height.toFloat()
            }
            .pointerInput(state) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (pan.x != 0f) {
                        state.followX = false
                        val perPx = state.xSpan / viewWidth
                        state.xEnd = (state.xEnd - pan.x * perPx).coerceAtLeast(0f)
                    }
                    if (pan.y != 0f) {
                        state.autoY = false
                        val perPx = (state.yMax - state.yMin) / viewHeight
                        val mid = (state.yMax + state.yMin) / 2f + pan.y * perPx
                        val half = (state.yMax - state.yMin) / 2f
                        state.yMin = mid - half
                        state.yMax = mid + half
                    }
                    if (zoom != 1f && zoom > 0f) {
                        // detectTransformGestures reports a uniform zoom factor;
                        // apply it to both axes simultaneously.
                        state.followX = false
                        state.xSpan = (state.xSpan / zoom).coerceIn(MIN_X_SPAN, 1e7f)
                        state.autoY = false
                        val mid = (state.yMax + state.yMin) / 2f
                        val half = ((state.yMax - state.yMin) / 2f) / zoom
                        if (half * 2 > MIN_Y_SPAN) {
                            state.yMin = mid - half
                            state.yMax = mid + half
                        }
                    }
                }
            }
    ) {
        // Read version so this draw invalidates on every new sample
        @Suppress("UNUSED_EXPRESSION") version

        val series = seriesProvider()
        if (state.autoY && series.isNotEmpty()) {
            autoFitY(series, state.xEnd - state.xSpan, state.xEnd, sampleIdx)?.let { (lo, hi) ->
                if (lo != state.yMin) state.yMin = lo
                if (hi != state.yMax) state.yMax = hi
            }
        }
        PlotRenderer.render(
            PlotFrame(state.xEnd, state.xSpan, state.yMin, state.yMax, sampleIdx, series),
            drawContext.canvas.nativeCanvas,
            size.width,
            size.height,
            textPaint
        )
    }
}

/** "Nice" tick values covering [from, to] (used for both axes). */
internal fun niceTicks(from: Double, to: Double, targetCount: Int): List<Float> {
    if (to <= from || !from.isFinite() || !to.isFinite()) return emptyList()
    val rawStep = (to - from) / targetCount
    val mag = 10.0.pow(floor(log10(rawStep)))
    val norm = rawStep / mag
    val step = when {
        norm >= 5 -> 5 * mag
        norm >= 2 -> 2 * mag
        else -> mag
    }
    val ticks = mutableListOf<Float>()
    var t = ceil(from / step) * step
    while (t <= to + step * 1e-6) {
        ticks.add(t.toFloat())
        t += step
    }
    return ticks
}

private fun formatStep(v: Float): String =
    if (abs(v) >= 10000f) "%.0fk".format(v / 1000f) else "%.0f".format(v)

private fun formatValue(v: Double): String = when {
    abs(v) >= 1000 -> "%.0f".format(v)
    abs(v) >= 1 -> "%.1f".format(v)
    abs(v) >= 0.001 -> "%.3f".format(v)
    v == 0.0 -> "0"
    else -> "%.1e".format(v)
}
