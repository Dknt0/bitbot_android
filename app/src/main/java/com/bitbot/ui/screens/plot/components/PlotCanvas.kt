package com.bitbot.ui.screens.plot.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
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
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** One drawable curve. [values] is oldest..newest, aligned to the tail of [PlotFrame.xs]. */
class PlotSeries(val color: Color, val values: List<Double>)

/**
 * Immutable snapshot of everything needed to draw the plot once. [xs] holds the
 * x value (kernel periods_count) of every recorded frame; a series' i-th value
 * (from the end) corresponds to xs' i-th value from the end — the kernel loop
 * and the poll rate differ, so spacing between samples is not uniform.
 */
class PlotFrame(
    val xEnd: Float,
    val xSpan: Float,
    val yMin: Float,
    val yMax: Float,
    val xs: List<Double>,
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
/** One legend entry drawn in exported images: color swatch + label. */
data class LegendEntry(val colorARGB: Int, val label: String)

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
        fun yToPx(v: Double): Float {
            val t = ((v - yMin) / ySpan).toFloat().coerceIn(-0.5f, 1.5f)
            return plotBottom - t * plotH
        }

        val gridPaint = Paint().apply { color = gridColor; strokeWidth = 1f; isAntiAlias = true }
        val gridFaint = Paint().apply { color = gridColor; alpha = 100; strokeWidth = 1f }
        val curvePaint = Paint().apply {
    style = Paint.Style.STROKE
    strokeWidth = 2f
    isAntiAlias = true
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
}

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

        // curves — iterate frames by real x value (poll spacing ≠ kernel spacing)
        val xs = frame.xs
        for (s in frame.series) {
            val n = s.values.size
            if (n == 0) continue
            val offset = xs.size - n // index into xs of this series' first value
            var jFrom = 0
            var jTo = -1
            for (j in 0 until n) {
                val x = xs[j + offset]
                if (x < xStart) continue
                if (x > xEnd) break
                if (jTo < 0) jFrom = j
                jTo = j
            }
            if (jTo < jFrom) continue

            // Density is SAMPLES per pixel (not x-span per pixel — kernel
            // periods per pixel is >1 even for sparse sampled data).
            val samplesPerPixel = (jTo - jFrom + 1).toFloat() / plotW

            curvePaint.color = s.color.toArgb()
            val path = android.graphics.Path()
            if (samplesPerPixel <= 1.5f) {
                var started = false
                for (j in jFrom..jTo) {
                    val v = s.values[j]
                    if (!v.isFinite()) continue
                    val px = xToPx(xs[j + offset].toFloat())
                    val py = yToPx(v)
                    if (!started) {
                        path.moveTo(px, py); started = true
                    } else {
                        path.lineTo(px, py)
                    }
                }
                curvePaint.strokeWidth = 2f
            } else {
                // Per-pixel min/max columns, connected between columns so the
                // trace stays continuous at moderate densities.
                var curCol = Int.MIN_VALUE
                var lo = 0.0
                var hi = 0.0
                var colLastY = 0f
                var prevCol = Int.MIN_VALUE
                var prevColLastY = 0f
                fun flushColumn() {
                    val px = plotLeft + curCol
                    if (prevCol != Int.MIN_VALUE) {
                        path.moveTo(plotLeft + prevCol, prevColLastY)
                        path.lineTo(px, yToPx(lo))
                    }
                    path.moveTo(px, yToPx(lo))
                    path.lineTo(px, yToPx(hi))
                }
                for (j in jFrom..jTo) {
                    val v = s.values[j]
                    if (!v.isFinite()) continue
                    val col = floor(xToPx(xs[j + offset].toFloat()) - plotLeft).toInt()
                    if (col != curCol) {
                        if (curCol != Int.MIN_VALUE) {
                            flushColumn()
                            prevCol = curCol
                            prevColLastY = colLastY
                        }
                        curCol = col
                        lo = v
                        hi = v
                    } else {
                        if (v < lo) lo = v
                        if (v > hi) hi = v
                    }
                    colLastY = yToPx(v)
                }
                if (curCol != Int.MIN_VALUE) {
                    flushColumn()
                }
                curvePaint.strokeWidth = 1.5f
            }
            canvas.drawPath(path, curvePaint)
        }
    }

    fun renderToBitmap(
        frame: PlotFrame,
        plotWidthPx: Int,
        plotHeightPx: Int,
        textScale: Float,
        legend: List<LegendEntry> = emptyList()
    ): Bitmap {
        val legendPaint = Paint().apply {
            textSize = 10f * textScale
            isAntiAlias = true
            color = axisTextColor
        }
        val swatch = legendPaint.textSize
        val gap = 6f * textScale
        val entryGap = 14f * textScale
        val rowH = swatch * 1.9f

        // Wrap entries into rows that fit the plot width
        val rows = mutableListOf<MutableList<Pair<LegendEntry, Float>>>()
        var current = mutableListOf<Pair<LegendEntry, Float>>()
        var x = 0f
        legend.forEach { entry ->
            val w = swatch + gap + legendPaint.measureText(entry.label) + entryGap
            if (x + w > plotWidthPx && current.isNotEmpty()) {
                rows += current
                current = mutableListOf()
                x = 0f
            }
            current += entry to w
            x += w
        }
        if (current.isNotEmpty()) rows += current

        val legendHeight = if (rows.isEmpty()) 0f else rows.size * rowH + rowH * 0.6f
        val bmp = Bitmap.createBitmap(
            max(1, plotWidthPx),
            max(1, (plotHeightPx + legendHeight).toInt()),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bmp)
        canvas.drawColor(0xFF212121.toInt()) // match the on-screen dark plot background
        render(frame, canvas, bmp.width.toFloat(), plotHeightPx.toFloat(), textPaint(textScale))

        if (rows.isNotEmpty()) {
            val separator = Paint().apply { color = gridColor; alpha = 100; strokeWidth = 1f }
            canvas.drawLine(0f, plotHeightPx + 2f, bmp.width.toFloat(), plotHeightPx + 2f, separator)
            var y = plotHeightPx + rowH * 0.95f
            rows.forEach { row ->
                var px = 8f * textScale
                row.forEach { (entry, w) ->
                    legendPaint.color = entry.colorARGB
                    canvas.drawRoundRect(px, y - swatch, px + swatch, y, 2f, 2f, legendPaint)
                    legendPaint.color = axisTextColor
                    canvas.drawText(entry.label, px + swatch + gap, y, legendPaint)
                    px += w
                }
                y += rowH
            }
        }
        return bmp
    }
}

/**
 * Auto-fit y range over the visible samples; null when no visible samples.
 * Samples are located by their real x values ([xs]), not uniform spacing.
 */
internal fun autoFitY(series: List<PlotSeries>, xs: List<Double>, xStart: Float, xEnd: Float): Pair<Float, Float>? {
    var lo = Double.POSITIVE_INFINITY
    var hi = Double.NEGATIVE_INFINITY
    for (s in series) {
        val n = s.values.size
        if (n == 0) continue
        val offset = xs.size - n
        for (j in 0 until n) {
            val x = xs[j + offset]
            if (x < xStart) continue
            if (x > xEnd) break
            val v = s.values[j]
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
 * - follow mode: window = [latestTime - horizon, latestTime] in seconds.
 * - drag pans, pinch zooms (x and y independently).
 * - per-pixel min/max decimation when more samples than pixels.
 */
@Composable
fun PlotCanvas(
    state: PlotViewState,
    versionFlow: StateFlow<Long>,
    horizonSpanX: Float,
    xsProvider: () -> List<Double>,
    arrivalNanosProvider: () -> Long,
    seriesProvider: () -> List<PlotSeries>,
    modifier: Modifier = Modifier
) {
    // Collect the recorder version INSIDE the canvas so only this node
    // recomposes on every new sample (the rest of the screen stays at 5 Hz).
    val version by versionFlow.collectAsState()
    var viewWidth by remember { mutableStateOf(1f) }
    var viewHeight by remember { mutableStateOf(1f) }

    // Follow mode driven by the WALL CLOCK on every animation frame: the
    // right edge advances at exactly 1 s/s (last sample time + elapsed since
    // its arrival, extrapolation capped), independent of reply burstiness.
    // Chasing per-sample targets instead made the scroll speed jitter.
    LaunchedEffect(state.followX, horizonSpanX) {
        while (state.followX) {
            withFrameNanos { frameNanos ->
                val lastX = xsProvider().lastOrNull() ?: return@withFrameNanos
                val elapsed = (frameNanos - arrivalNanosProvider()) / 1e9f
                val xEnd = (lastX + elapsed.coerceIn(0f, 0.2f)).toFloat()
                if (xEnd != state.xEnd) state.xEnd = xEnd
                val span = maxOf(horizonSpanX, MIN_X_SPAN)
                if (span != state.xSpan) state.xSpan = span
            }
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
                detectTransformGestures { centroid, pan, zoom, _ ->
                    if (pan.x != 0f) {
                        if (state.followX) state.followX = false
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
                        if (state.followX) state.followX = false
                        // Zoom anchored at the pinch centroid (detectTransformGestures
                        // reports a uniform factor; apply it to both axes).
                        val cx = (state.xEnd - state.xSpan) + centroid.x / viewWidth * state.xSpan
                        val cy = state.yMin + (1f - centroid.y / viewHeight) * (state.yMax - state.yMin)
                        state.followX = false
                        state.xSpan = (state.xSpan / zoom).coerceIn(MIN_X_SPAN, 1e9f)
                        state.xEnd = cx + (state.xEnd - cx) / zoom
                        val halfY = ((state.yMax - state.yMin) / 2f) / zoom
                        if (halfY * 2 > MIN_Y_SPAN) {
                            state.autoY = false
                            state.yMin = cy - (cy - state.yMin) / zoom
                            state.yMax = cy + (state.yMax - cy) / zoom
                        }
                    }
                }
            }
    ) {
        // Read version so this draw invalidates on every new sample
        @Suppress("UNUSED_EXPRESSION") version

        val liveXEnd = state.xEnd
        val series = seriesProvider()
        val xs = xsProvider()
        if (state.autoY && series.isNotEmpty()) {
            autoFitY(series, xs, liveXEnd - state.xSpan, liveXEnd)?.let { (lo, hi) ->
                if (lo != state.yMin) state.yMin = lo
                if (hi != state.yMax) state.yMax = hi
            }
        }
        PlotRenderer.render(
            PlotFrame(liveXEnd, state.xSpan, state.yMin, state.yMax, xs, series),
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

private fun formatStep(v: Float): String {
    val t = "%.2f".format(v).trimEnd('0').trimEnd('.')
    return "${t}s"
}

private fun formatValue(v: Double): String = when {
    abs(v) >= 1000 -> "%.0f".format(v)
    abs(v) >= 1 -> "%.1f".format(v)
    abs(v) >= 0.001 -> "%.3f".format(v)
    v == 0.0 -> "0"
    else -> "%.1e".format(v)
}
