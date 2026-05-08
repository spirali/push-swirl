package org.kreatrix.pushswirl

import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

data class ChartPoint(val x: Float, val y: Float)

/**
 * @param showDots             whether to draw a circle at each data point
 * @param lineAlpha            opacity of the connecting line (1f = fully opaque)
 * @param lineStrokeMultiplier relative thickness of the line (base stroke × this)
 * @param showInLegend         whether this series appears in the legend
 * @param pathEffect           optional dash/dot pattern for the line
 * @param countForYScale       whether these points contribute to the y-axis max (set false for regression lines)
 */
data class ChartSeries(
    val color: Color,
    val label: String,
    val points: List<ChartPoint>,
    val showDots: Boolean = true,
    val lineAlpha: Float = 1f,
    val lineStrokeMultiplier: Float = 1f,
    val showInLegend: Boolean = true,
    val pathEffect: PathEffect? = null,
    val countForYScale: Boolean = true
)

/** Ordinary least-squares regression line; returns the two endpoints at xMin and xMax, or null if fewer than 2 points. */
fun linearRegression(points: List<ChartPoint>): List<ChartPoint>? {
    if (points.size < 2) return null
    val n = points.size.toDouble()
    val sumX  = points.sumOf { it.x.toDouble() }
    val sumY  = points.sumOf { it.y.toDouble() }
    val sumXY = points.sumOf { it.x.toDouble() * it.y }
    val sumX2 = points.sumOf { it.x.toDouble() * it.x }
    val denom = n * sumX2 - sumX * sumX
    if (denom == 0.0) return null
    val slope     = (n * sumXY - sumX * sumY) / denom
    val intercept = (sumY - slope * sumX) / n
    val xMin = points.minOf { it.x }
    val xMax = points.maxOf { it.x }
    return listOf(
        ChartPoint(xMin, (intercept + slope * xMin).toFloat()),
        ChartPoint(xMax, (intercept + slope * xMax).toFloat())
    )
}

/** Weighted moving average over [window] previous points (inclusive), with linearly increasing weights. */
fun movingAverage(points: List<ChartPoint>, window: Int = 3): List<ChartPoint> =
    points.mapIndexed { i, pt ->
        val slice = points.subList(maxOf(0, i - window + 1), i + 1)
        val weights = slice.indices.map { (it + 1).toFloat() }
        val weightedSum = slice.zip(weights).sumOf { (p, w) -> (p.y * w).toDouble() }.toFloat()
        ChartPoint(pt.x, weightedSum / weights.sum())
    }

private val Y_TICK_CANDIDATES: List<Float> by lazy {
    buildSet<Float> {
        for (exp in -2..7) {
            val mag = 10f.pow(exp)
            add(mag); add(2f * mag); add(5f * mag)
        }
        // whole-minute steps in seconds (60 is not a power-of-10 multiple)
        addAll(listOf(15f, 30f, 60f, 90f, 120f, 180f, 240f, 300f, 600f, 900f, 1800f, 3600f, 7200f, 21600f, 86400f))
        // sub-hour and multi-hour steps
        addAll(listOf(0.25f, 0.5f, 0.75f, 1.5f, 2.5f, 3f, 4f, 6f, 8f, 12f, 24f, 48f, 72f))
    }.filter { it > 0f }.sorted()
}

/**
 * Chooses nice Y-axis tick values by trying candidate step sizes and scoring them via the
 * formatter: steps that produce duplicate labels are rejected; same-unit labels preferred.
 * Returns (chartYMax, tickValues) where chartYMax >= dataMax and is a multiple of the step.
 */
private val NICE_MINUTE_LABELS = setOf("1m", "2m", "5m", "10m", "15m")
private val NICE_HOUR_LABELS   = setOf("1h", "2h", "4h", "8h", "12h")

/**
 * Picks a nice step size from [Y_TICK_CANDIDATES].
 * [ticksForStep] generates the tick list for a given step.
 * [sameUnitLabels] selects which labels participate in the same-unit check
 * (Y skips the anchored "0x" label; X uses all labels).
 */
private fun pickNiceStep(
    targetCount: Int,
    fallbackStep: Float,
    ticksForStep: (Float) -> List<Float>,
    formatter: (Float) -> String,
    sameUnitLabels: (List<String>) -> List<String> = { it }
): Float {
    val validRange = 2..(targetCount + 3)

    fun score(step: Float, penalizeMixedUnit: Boolean): Int {
        val ticks = ticksForStep(step)
        if (ticks.size !in validRange) return Int.MAX_VALUE
        val labels = ticks.map(formatter)
        if (labels.toSet().size != labels.size) return Int.MAX_VALUE
        val check = sameUnitLabels(labels)
        val allH = check.all { 'h' in it }
        val allM = check.all { 'm' in it && 's' !in it && 'h' !in it }
        val allS = check.all { 's' in it && 'm' !in it }
        val allD = check.all { 'd' in it && 'h' !in it }
        // Restrict to user-friendly step sizes per unit, detected via the step's own label
        val stepLabel = formatter(step)
        if (allM && stepLabel !in NICE_MINUTE_LABELS) return Int.MAX_VALUE
        if (allH && stepLabel !in NICE_HOUR_LABELS)   return Int.MAX_VALUE
        val sameUnit = allH || allM || allS || allD
        return (if (penalizeMixedUnit && !sameUnit) 100 else 0) + abs(ticks.size - targetCount) * 10 + ticks.size
    }

    val preferredStep = Y_TICK_CANDIDATES.minByOrNull { score(it, true) } ?: fallbackStep
    // If the same-unit winner is too sparse, allow cross-unit steps
    return if (ticksForStep(preferredStep).size < 4) {
        Y_TICK_CANDIDATES.minByOrNull { score(it, false) } ?: preferredStep
    } else preferredStep
}

fun niceYTicks(dataMax: Float, formatter: (Float) -> String, targetCount: Int = 5): Pair<Float, List<Float>> {
    fun ticksForStep(step: Float): List<Float> {
        val count = ceil(dataMax / step).toInt() + 1
        return generateSequence(0f) { it + step }.take(count).toList()
    }
    val step = pickNiceStep(targetCount, dataMax / targetCount, ::ticksForStep, formatter, sameUnitLabels = { it.drop(1) })
    val ticks = ticksForStep(step)
    return Pair(ticks.last(), ticks)
}

/** Nice X-axis ticks within [xMin]..[xMax]. */
fun niceXTicks(xMin: Float, xMax: Float, formatter: (Float) -> String, targetCount: Int = 5): List<Float> {
    val range = xMax - xMin
    if (range <= 0f) return listOf(xMin)
    fun ticksForStep(step: Float): List<Float> {
        val first = ceil(xMin / step) * step
        return generateSequence(first) { it + step }.takeWhile { it <= xMax + step * 0.001f }.toList()
    }
    val step = pickNiceStep(targetCount, range / targetCount, ::ticksForStep, formatter)
    return ticksForStep(step)
}

@Composable
fun LineChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    yAxisFormatter: (Float) -> String = { "%.0f".format(it) },
    xAxisFormatter: ((Float) -> String)? = null,
    xMilestoneInterval: Float? = null
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val density   = LocalDensity.current

    val allPoints = series.flatMap { it.points }

    if (allPoints.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No data", color = textColor.copy(alpha = 0.4f))
        }
    } else {
        val xMin = allPoints.minOf { it.x }
        val xMax = allPoints.maxOf { it.x }
        val scalePoints = series.filter { it.countForYScale }.flatMap { it.points }
        val dataYMax = (if (scalePoints.isEmpty()) allPoints else scalePoints).maxOf { it.y }.coerceAtLeast(1f)
        val (chartYMax, yTicks) = niceYTicks(dataYMax, yAxisFormatter)

        Canvas(modifier = modifier) {
            val leftPad    = with(density) { 68.dp.toPx() }
            val rightPad   = with(density) {  8.dp.toPx() }
            val topPad     = with(density) {  6.dp.toPx() }
            val botPad     = with(density) { if (xAxisFormatter != null) 26.dp.toPx() else 12.dp.toPx() }
            val textSizePx = with(density) { 10.sp.toPx() }
            val baseStroke = with(density) {  2.dp.toPx() }
            val dotRadius  = with(density) {  3.dp.toPx() }

            val chartLeft   = leftPad
            val chartRight  = size.width - rightPad
            val chartTop    = topPad
            val chartBottom = size.height - botPad
            val chartWidth  = chartRight - chartLeft
            val chartHeight = chartBottom - chartTop

            fun mapX(x: Float): Float =
                if (xMax == xMin) chartLeft + chartWidth / 2f
                else chartLeft + (x - xMin) / (xMax - xMin) * chartWidth

            fun mapY(y: Float): Float = chartBottom - (y / chartYMax) * chartHeight

            val paint = AndroidPaint().apply {
                color       = textColor.toArgb()
                textSize    = textSizePx
                textAlign   = AndroidPaint.Align.RIGHT
                typeface    = Typeface.DEFAULT
                isAntiAlias = true
            }

            // Grid lines + Y axis labels
            for (yValue in yTicks) {
                val yPixel = mapY(yValue)
                drawLine(gridColor, Offset(chartLeft, yPixel), Offset(chartRight, yPixel), 1.5f)
                drawContext.canvas.nativeCanvas.drawText(
                    yAxisFormatter(yValue),
                    chartLeft - with(density) { 4.dp.toPx() },
                    yPixel + textSizePx / 3f,
                    paint
                )
            }

            // Left axis line
            drawLine(
                gridColor.copy(alpha = 0.5f),
                Offset(chartLeft, chartTop),
                Offset(chartLeft, chartBottom),
                1.5f
            )

            // Milestone vertical lines (e.g. every 30 days)
            if (xMilestoneInterval != null && xMilestoneInterval > 0f) {
                val firstMilestone = Math.ceil((xMin / xMilestoneInterval).toDouble()).toInt() * xMilestoneInterval
                var milestone = firstMilestone
                while (milestone <= xMax) {
                    val xPixel = mapX(milestone)
                    drawLine(
                        color = Color.Red.copy(alpha = 0.5f),
                        start = Offset(xPixel, chartTop),
                        end   = Offset(xPixel, chartBottom),
                        strokeWidth = with(density) { 1.dp.toPx() }
                    )
                    milestone += xMilestoneInterval
                }
            }

            // X axis labels
            if (xAxisFormatter != null) {
                val xPaint = AndroidPaint().apply {
                    color       = textColor.toArgb()
                    textSize    = textSizePx
                    textAlign   = AndroidPaint.Align.CENTER
                    typeface    = Typeface.DEFAULT
                    isAntiAlias = true
                }
                for (xValue in niceXTicks(xMin, xMax, xAxisFormatter)) {
                    drawContext.canvas.nativeCanvas.drawText(
                        xAxisFormatter(xValue),
                        mapX(xValue),
                        chartBottom + textSizePx * 1.8f,
                        xPaint
                    )
                }
            }

            // Draw lines first, then dots on top
            series.forEach { s ->
                val sorted = s.points.sortedBy { it.x }
                if (sorted.size >= 2) {
                    val path = Path()
                    sorted.forEachIndexed { i, pt ->
                        val cx = mapX(pt.x); val cy = mapY(pt.y)
                        if (i == 0) path.moveTo(cx, cy) else path.lineTo(cx, cy)
                    }
                    drawPath(
                        path  = path,
                        color = s.color.copy(alpha = s.lineAlpha),
                        style = Stroke(
                            width      = baseStroke * s.lineStrokeMultiplier,
                            cap        = StrokeCap.Round,
                            join       = StrokeJoin.Round,
                            pathEffect = s.pathEffect
                        )
                    )
                }
            }

            series.forEach { s ->
                if (!s.showDots) return@forEach
                s.points.forEach { pt ->
                    drawCircle(s.color, radius = dotRadius, center = Offset(mapX(pt.x), mapY(pt.y)))
                }
            }
        }
    }
}

data class ScatterPoint(val x: Float, val y: Float, val color: Color)

@Composable
fun ScatterChart(
    points: List<ScatterPoint>,
    modifier: Modifier = Modifier,
    xAxisFormatter: (Float) -> String = { "%.0f".format(it) },
    yAxisFormatter: (Float) -> String = { "%.0f".format(it) },
    regressionLine: List<ChartPoint>? = null,
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val density   = LocalDensity.current

    if (points.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No data", color = textColor.copy(alpha = 0.4f))
        }
        return
    }

    val xMin = points.minOf { it.x }
    val xMax = points.maxOf { it.x }.coerceAtLeast(xMin + 1f)
    val dataYMax = points.maxOf { it.y }.coerceAtLeast(1f)
    val (chartYMax, yTicks) = niceYTicks(dataYMax, yAxisFormatter)

    Canvas(modifier = modifier) {
        val leftPad    = with(density) { 68.dp.toPx() }
        val rightPad   = with(density) {  8.dp.toPx() }
        val topPad     = with(density) {  6.dp.toPx() }
        val botPad     = with(density) { 26.dp.toPx() }
        val textSizePx = with(density) { 10.sp.toPx() }
        val dotRadius  = with(density) {  4.dp.toPx() }

        val chartLeft   = leftPad
        val chartRight  = size.width - rightPad
        val chartTop    = topPad
        val chartBottom = size.height - botPad
        val chartWidth  = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        fun mapX(x: Float): Float =
            chartLeft + (x - xMin) / (xMax - xMin) * chartWidth

        fun mapY(y: Float): Float = chartBottom - (y / chartYMax) * chartHeight

        val yPaint = AndroidPaint().apply {
            color       = textColor.toArgb()
            textSize    = textSizePx
            textAlign   = AndroidPaint.Align.RIGHT
            typeface    = Typeface.DEFAULT
            isAntiAlias = true
        }
        val xPaint = AndroidPaint().apply {
            color       = textColor.toArgb()
            textSize    = textSizePx
            textAlign   = AndroidPaint.Align.CENTER
            typeface    = Typeface.DEFAULT
            isAntiAlias = true
        }

        // Y grid lines + labels
        for (yValue in yTicks) {
            val yPixel = mapY(yValue)
            drawLine(gridColor, Offset(chartLeft, yPixel), Offset(chartRight, yPixel), 1.5f)
            drawContext.canvas.nativeCanvas.drawText(
                yAxisFormatter(yValue),
                chartLeft - with(density) { 4.dp.toPx() },
                yPixel + textSizePx / 3f,
                yPaint
            )
        }

        // Left axis line
        drawLine(gridColor.copy(alpha = 0.5f), Offset(chartLeft, chartTop), Offset(chartLeft, chartBottom), 1.5f)

        // X axis labels
        for (xValue in niceXTicks(xMin, xMax, xAxisFormatter)) {
            drawContext.canvas.nativeCanvas.drawText(
                xAxisFormatter(xValue),
                mapX(xValue),
                chartBottom + textSizePx * 1.8f,
                xPaint
            )
        }

        // Dots
        points.forEach { pt ->
            drawCircle(pt.color, radius = dotRadius, center = Offset(mapX(pt.x), mapY(pt.y)))
        }

        // Regression line
        if (regressionLine != null && regressionLine.size >= 2) {
            val r0 = regressionLine.first()
            val r1 = regressionLine.last()
            val baseStroke = with(density) { 2.dp.toPx() }
            drawLine(
                color       = textColor.copy(alpha = 0.65f),
                start       = Offset(
                    mapX(r0.x).coerceIn(chartLeft, chartRight),
                    mapY(r0.y).coerceIn(chartTop, chartBottom)
                ),
                end         = Offset(
                    mapX(r1.x).coerceIn(chartLeft, chartRight),
                    mapY(r1.y).coerceIn(chartTop, chartBottom)
                ),
                strokeWidth = baseStroke * 1.5f,
                pathEffect  = PathEffect.dashPathEffect(floatArrayOf(20f, 10f))
            )
        }
    }
}

@Composable
fun ChartLegend(series: List<ChartSeries>) {
    val visible = series.filter { it.showInLegend }
    if (visible.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        visible.forEach { s ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Canvas(modifier = Modifier.size(10.dp)) { drawCircle(s.color) }
                Text(s.label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
