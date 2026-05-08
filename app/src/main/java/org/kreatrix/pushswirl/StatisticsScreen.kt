package org.kreatrix.pushswirl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val COLOR_SMALL  = Color(0xFF66BB6A)
private val COLOR_MEDIUM = Color(0xFF42A5F5)
private val COLOR_LARGE  = Color(0xFFFFA726)
private val COLOR_XL     = Color(0xFFAB47BC)
private val COLOR_GAP    = Color(0xFF26C6DA)

private const val CHART_DENSE_THRESHOLD = 30

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(viewModel: SessionViewModel) {
    BackHandler { viewModel.navigateTo(AppScreen.Home) }

    val stats = viewModel.stats
    val selectedInterval = viewModel.statsTimeInterval
    val sessions = viewModel.sessions

    val day0Date = viewModel.day0Date
    val onSurface = MaterialTheme.colorScheme.onSurface
    val ttdVisibleSizes = viewModel.ttdVisibleSizes

    // Compute filtered + sorted sessions for charts
    val chartData = remember(sessions, selectedInterval, day0Date, onSurface, ttdVisibleSizes) {
        val days = selectedInterval.days
        val filtered = if (days == null) sessions
        else {
            val cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
            sessions.filter { it.timestamp >= cutoff }
        }
        val sorted = filtered.sortedBy { it.timestamp }

        fun sessionX(session: Session, idx: Int): Float =
            if (day0Date != null) (session.timestamp - day0Date) / 86_400_000f
            else idx.toFloat()

        val regDash = PathEffect.dashPathEffect(floatArrayOf(20f, 10f))

        fun buildSeries(color: Color, label: String, points: List<ChartPoint>, maOnly: Boolean, regColor: Color = onSurface): List<ChartSeries> {
            if (points.isEmpty()) return emptyList()
            val maPoints  = movingAverage(points)
            val regPoints = linearRegression(points)
            val regSeries = if (regPoints != null) listOf(
                ChartSeries(color = regColor, label = "$label Trend", points = regPoints,
                    showDots = false, lineAlpha = 0.55f, lineStrokeMultiplier = 1.8f,
                    showInLegend = false, pathEffect = regDash, countForYScale = false)
            ) else emptyList()
            return if (maOnly) {
                listOf(ChartSeries(color = color, label = label, points = maPoints,
                    showDots = false, lineAlpha = 1f, lineStrokeMultiplier = 1.6f)) + regSeries
            } else {
                listOf(
                    ChartSeries(color = color, label = label, points = points,
                        showDots = true, lineAlpha = 0.3f, lineStrokeMultiplier = 0.7f),
                    ChartSeries(color = color, label = "$label MA", points = maPoints,
                        showDots = false, lineAlpha = 1f, lineStrokeMultiplier = 1.6f, showInLegend = false)
                ) + regSeries
            }
        }

        val ttdInputs = listOf(
            Triple(PhaseSize.SMALL,  "Small",  COLOR_SMALL),
            Triple(PhaseSize.MEDIUM, "Medium", COLOR_MEDIUM),
            Triple(PhaseSize.LARGE,  "Large",  COLOR_LARGE),
            Triple(PhaseSize.XL,     "XL",     COLOR_XL),
        ).filter { (size, _, _) -> size in ttdVisibleSizes }.map { (size, label, color) ->
            val points = sorted.mapIndexedNotNull { idx, session ->
                session.phases.find { it.size == size }?.let { phase ->
                    ChartPoint(sessionX(session, idx), phase.ttdSeconds.toFloat())
                }
            }
            Triple(color, label, points)
        }
        val ttdMaOnly = ttdInputs.any { (_, _, points) -> points.size > CHART_DENSE_THRESHOLD }
        val ttdSeries = ttdInputs.flatMap { (color, label, points) ->
            val tintedReg = Color(
                red   = onSurface.red   * 0.55f + color.red   * 0.45f,
                green = onSurface.green * 0.55f + color.green * 0.45f,
                blue  = onSurface.blue  * 0.55f + color.blue  * 0.45f,
                alpha = 1f
            )
            buildSeries(color, label, points, ttdMaOnly, regColor = tintedReg)
        }

        val gapSeries = if (sorted.size < 2) emptyList()
        else {
            val points = sorted.indices.drop(1).map { idx ->
                val gapHours = (sorted[idx].timestamp - sorted[idx - 1].timestamp).toFloat() / 3_600_000f
                ChartPoint(sessionX(sorted[idx], idx), gapHours)
            }
            buildSeries(COLOR_GAP, "Gap", points, points.size > CHART_DENSE_THRESHOLD)
        }

        val lengthPoints = sorted.mapIndexed { idx, session ->
            ChartPoint(sessionX(session, idx), session.totalSeconds.toFloat() / 60f)
        }
        val lengthSeries = buildSeries(COLOR_MEDIUM, "Length", lengthPoints, lengthPoints.size > CHART_DENSE_THRESHOLD)

        Triple(ttdSeries, gapSeries, lengthSeries)
    }
    val (ttdSeries, gapSeries, lengthSeries) = chartData

    // Per-phase scatter: X = gap to previous session (hours), Y = TTD (seconds), color encodes age
    val ttdGapScatter = remember(sessions, selectedInterval) {
        val days = selectedInterval.days
        val filtered = if (days == null) sessions
        else {
            val cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
            sessions.filter { it.timestamp >= cutoff }
        }
        val sorted = filtered.sortedBy { it.timestamp }

        listOf(
            Triple(PhaseSize.SMALL,  "Small",  COLOR_SMALL),
            Triple(PhaseSize.MEDIUM, "Medium", COLOR_MEDIUM),
            Triple(PhaseSize.LARGE,  "Large",  COLOR_LARGE),
            Triple(PhaseSize.XL,     "XL",     COLOR_XL),
        ).mapNotNull { (size, label, color) ->
            if (sorted.size < 2) return@mapNotNull null
            val raw = sorted.indices.drop(1).mapNotNull { idx ->
                val phase = sorted[idx].phases.find { it.size == size } ?: return@mapNotNull null
                val gapHours = (sorted[idx].timestamp - sorted[idx - 1].timestamp).toFloat() / 3_600_000f
                Pair(gapHours, phase.ttdSeconds.toFloat())
            }
            if (raw.isEmpty()) return@mapNotNull null
            val n = raw.size
            val points = raw.mapIndexed { i, (x, y) ->
                val age = if (n == 1) 1f else i.toFloat() / (n - 1).toFloat()
                ScatterPoint(x, y, color.copy(alpha = 0.2f + age * 0.8f))
            }
            val regLine = linearRegression(raw.map { (x, y) -> ChartPoint(x, y) })
            Triple(label, Pair(color, regLine), points)
        }
    }

    val xAxisFormatter: ((Float) -> String)? = if (day0Date != null) { x -> "D${x.toInt()}" } else null
    val xMilestoneInterval: Float? = if (day0Date != null) 30f else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    TextButton(onClick = { viewModel.navigateTo(AppScreen.Home) }) {
                        Text("Back", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Time interval filter chips — two rows so they fit on small screens
            val intervalRows = StatsTimeInterval.entries.chunked(4)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                intervalRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        row.forEach { interval ->
                            FilterChip(
                                modifier = Modifier.weight(1f),
                                selected = selectedInterval == interval,
                                onClick = { viewModel.updateStatsTimeInterval(interval) },
                                label = { Text(interval.label, maxLines = 1) }
                            )
                        }
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 4.dp))

            if (stats.totalSessions == 0) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No data for selected period",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Overall Statistics",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    StatCard(title = "Total Sessions", value = stats.totalSessions.toString())
                    StatCard(title = "Average Session Length", value = formatDuration(stats.sessionLength.toLong()))

                    if (stats.avgTimeBetweenSessions > 0) {
                        StatCard(
                            title = "Avg Time Between Sessions",
                            value = formatDurationLong(stats.avgTimeBetweenSessions.toLong())
                        )
                    }

                    // Session length chart
                    if (lengthSeries.isNotEmpty()) {
                        ChartCard(title = "Session Length") {
                            LineChart(
                                series = lengthSeries,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                yAxisFormatter = { min ->
                                    if (min >= 60f) {
                                        val h = (min / 60f).toInt(); val m = (min % 60f).toInt()
                                        if (m == 0) "${h}h" else "${h}h ${m}m"
                                    }
                                    else "${min.toInt()}m"
                                },
                                xAxisFormatter = xAxisFormatter,
                                xMilestoneInterval = xMilestoneInterval
                            )
                        }
                    }

                    // Time between sessions chart
                    if (gapSeries.isNotEmpty()) {
                        ChartCard(title = "Time Between Sessions") {
                            LineChart(
                                series = gapSeries,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                yAxisFormatter = { h ->
                                    when {
                                        h >= 24f -> "${(h / 24f).toInt()}d"
                                        h >= 1f  -> "${h.toInt()}h"
                                        else     -> "${(h * 60f).toInt()}m"
                                    }
                                },
                                xAxisFormatter = xAxisFormatter,
                                xMilestoneInterval = xMilestoneInterval
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Average TTD",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    if (stats.smallTTD  > 0) StatCard(title = "Small",  value = formatDuration(stats.smallTTD.toLong()))
                    if (stats.mediumTTD > 0) StatCard(title = "Medium", value = formatDuration(stats.mediumTTD.toLong()))
                    if (stats.largeTTD  > 0) StatCard(title = "Large",  value = formatDuration(stats.largeTTD.toLong()))
                    if (stats.xlTTD     > 0) StatCard(title = "XL",     value = formatDuration(stats.xlTTD.toLong()))

                    // TTD chart
                    ChartCard(title = "TTD") {
                        val ttdSizeEntries = listOf(
                            Triple(PhaseSize.SMALL,  "Small",  COLOR_SMALL),
                            Triple(PhaseSize.MEDIUM, "Medium", COLOR_MEDIUM),
                            Triple(PhaseSize.LARGE,  "Large",  COLOR_LARGE),
                            Triple(PhaseSize.XL,     "XL",     COLOR_XL),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ttdSizeEntries.forEach { (size, label, color) ->
                                val selected = size in ttdVisibleSizes
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.toggleTtdSize(size) },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = color.copy(alpha = 0.25f),
                                        selectedLabelColor = color
                                    )
                                )
                            }
                        }
                        if (ttdSeries.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LineChart(
                                series = ttdSeries,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                yAxisFormatter = { sec -> formatTtd(sec) },
                                xAxisFormatter = xAxisFormatter,
                                xMilestoneInterval = xMilestoneInterval
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ChartLegend(series = ttdSeries)
                        }
                    }

                    // TTD vs. Session Gap scatter charts
                    if (ttdGapScatter.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "TTD vs. Session Gap",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        ttdGapScatter.forEach { (label, colorAndReg, points) ->
                            val (color, regLine) = colorAndReg
                            ChartCard(title = label) {
                                ScatterChart(
                                    points = points,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp),
                                    xAxisFormatter = { h ->
                                        when {
                                            h >= 24f -> "${(h / 24f).toInt()}d"
                                            else     -> "${h.toInt()}h"
                                        }
                                    },
                                    yAxisFormatter = { sec ->
                                        formatTtd(sec)
                                    },
                                    regressionLine = regLine
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Canvas(modifier = Modifier.size(10.dp)) {
                                            drawCircle(color.copy(alpha = 0.2f))
                                        }
                                        Text("Older", style = MaterialTheme.typography.labelMedium)
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("Recent", style = MaterialTheme.typography.labelMedium)
                                        Canvas(modifier = Modifier.size(10.dp)) {
                                            drawCircle(color)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun StatCard(title: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun formatTtd(sec: Float): String {
    val m = (sec / 60).toInt()
    val s = (sec % 60).toInt()
    return when {
        m > 0 && s > 0 -> "${m}m ${s}s"
        m > 0          -> "${m}m"
        else           -> "${s}s"
    }
}

private fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
}

private fun formatDurationLong(seconds: Long): String {
    val days  = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val mins  = (seconds % 3600) / 60
    return when {
        days  > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${mins}m"
        else      -> "${mins}m"
    }
}
