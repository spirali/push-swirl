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

data class ChartPoint(val x: Float, val y: Float)

/**
 * @param showDots      whether to draw a circle at each data point
 * @param lineAlpha     opacity of the connecting line (1f = fully opaque)
 * @param lineStrokeMultiplier  relative thickness of the line (base stroke × this)
 * @param showInLegend  whether this series appears in the legend
 */
data class ChartSeries(
    val color: Color,
    val label: String,
    val points: List<ChartPoint>,
    val showDots: Boolean = true,
    val lineAlpha: Float = 1f,
    val lineStrokeMultiplier: Float = 1f,
    val showInLegend: Boolean = true
)

/** Simple moving average over [window] previous points (inclusive). */
fun movingAverage(points: List<ChartPoint>, window: Int = 3): List<ChartPoint> =
    points.mapIndexed { i, pt ->
        val slice = points.subList(maxOf(0, i - window + 1), i + 1)
        ChartPoint(pt.x, slice.map { it.y }.average().toFloat())
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
        val yMax = allPoints.maxOf { it.y }.coerceAtLeast(1f)

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

            fun mapY(y: Float): Float = chartBottom - (y / yMax) * chartHeight

            val paint = AndroidPaint().apply {
                color       = textColor.toArgb()
                textSize    = textSizePx
                textAlign   = AndroidPaint.Align.RIGHT
                typeface    = Typeface.DEFAULT
                isAntiAlias = true
            }

            // Grid lines + Y axis labels
            for (i in 0..4) {
                val fraction = i.toFloat() / 4f
                val yValue   = fraction * yMax
                val yPixel   = mapY(yValue)
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
                val tickCount = 5
                for (i in 0..tickCount) {
                    val fraction = i.toFloat() / tickCount.toFloat()
                    val xValue   = xMin + fraction * (xMax - xMin)
                    val xPixel   = mapX(xValue)
                    drawContext.canvas.nativeCanvas.drawText(
                        xAxisFormatter(xValue),
                        xPixel,
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
                            width = baseStroke * s.lineStrokeMultiplier,
                            cap   = StrokeCap.Round,
                            join  = StrokeJoin.Round
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
