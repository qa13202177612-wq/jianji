package com.jianji.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jianji.app.ui.theme.ChartExpense
import com.jianji.app.ui.theme.ChartIncome
import com.jianji.app.viewmodel.TrendPoint
import com.jianji.app.util.DailySpendPoint
import com.jianji.app.util.DateUtils
import java.util.Locale

data class PieSlice(
    val label: String,
    val value: Long,
    val color: Color
)

/**
 * 环形饼图：用 Canvas 绘制，无第三方图表库依赖，避免版本兼容问题。
 */
@Composable
fun DonutChart(
    slices: List<PieSlice>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 22.dp,
    center: @Composable () -> Unit = {}
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val total = slices.sumOf { it.value }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val diameter = (size.minDimension - stroke).coerceAtLeast(1f)
            val topLeft = Offset(
                x = (size.width - diameter) / 2f,
                y = (size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)

            if (total <= 0L) {
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                )
            } else {
                var startAngle = -90f
                slices.forEach { slice ->
                    val sweep = slice.value.toFloat() / total.toFloat() * 360f
                    if (sweep > 0.5f) {
                        drawArc(
                            color = slice.color,
                            startAngle = startAngle + 1f,
                            sweepAngle = (sweep - 2f).coerceAtLeast(0.5f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Butt)
                        )
                    }
                    startAngle += sweep
                }
            }
        }
        center()
    }
}

/**
 * 月度趋势折线图：收入/支出双线，带网格和圆点。
 * 坐标轴文字放在 Canvas 外部，保证中文在各版本 Compose 下都能正常显示。
 */
@Composable
fun TrendChart(
    points: List<TrendPoint>,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            if (points.isEmpty()) return@Canvas

            val maxValue = points.maxOf { maxOf(it.incomeCents, it.expenseCents) }
                .coerceAtLeast(1L)
                .toFloat()

            val left = 6.dp.toPx()
            val right = size.width - 6.dp.toPx()
            val top = 8.dp.toPx()
            val bottom = size.height - 8.dp.toPx()

            repeat(4) { index ->
                val y = top + (bottom - top) * index / 3f
                drawLine(
                    color = gridColor,
                    start = Offset(left, y),
                    end = Offset(right, y),
                    strokeWidth = 1f
                )
            }

            val step = if (points.size > 1) (right - left) / (points.size - 1) else 0f

            fun x(index: Int): Float = left + step * index
            fun y(value: Long): Float = bottom - (bottom - top) * (value / maxValue)

            fun buildPath(selector: (TrendPoint) -> Long): Path {
                val path = Path()
                points.forEachIndexed { index, point ->
                    val px = x(index)
                    val py = y(selector(point))
                    if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                return path
            }

            drawPath(
                path = buildPath { it.incomeCents },
                color = ChartIncome,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            drawPath(
                path = buildPath { it.expenseCents },
                color = ChartExpense,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            points.forEachIndexed { index, point ->
                drawCircle(
                    color = ChartIncome,
                    radius = 3.dp.toPx(),
                    center = Offset(x(index), y(point.incomeCents))
                )
                drawCircle(
                    color = ChartExpense,
                    radius = 3.dp.toPx(),
                    center = Offset(x(index), y(point.expenseCents))
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            points.forEach { point ->
                Text(
                    text = DateUtils.shortMonthLabel(point.month),
                    style = MaterialTheme.typography.bodySmall,
                    color = labelColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 每日支出柱状图：X 轴为当月 1 日到月末，Y 轴为当天支出。
 * 没有支出的日期不画柱子，最高的一天用实色突出。
 * 柱顶显示金额（单位：元），数值大的柱子优先显示；放不下时自动隐藏，避免重叠。
 */
@Composable
fun DailySpendChart(
    points: List<DailySpendPoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val lastDay = points.last().day
    val textMeasurer = rememberTextMeasurer()
    val valueStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 8.sp,
        lineHeight = 9.sp,
        color = labelColor
    )
    val peakValueStyle = valueStyle.copy(color = ChartExpense)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
        ) {
            val maxValue = points.maxOf { it.expenseCents }
                .coerceAtLeast(1L)
                .toFloat()

            val left = 6.dp.toPx()
            val right = size.width - 6.dp.toPx()
            // 顶部留出金额标签的位置
            val top = 16.dp.toPx()
            val bottom = size.height - 8.dp.toPx()

            repeat(4) { index ->
                val y = top + (bottom - top) * index / 3f
                drawLine(
                    color = gridColor,
                    start = Offset(left, y),
                    end = Offset(right, y),
                    strokeWidth = 1f
                )
            }

            val slot = (right - left) / points.size
            val barWidth = (slot * 0.55f).coerceAtLeast(2.dp.toPx())
            val peakValue = points.maxOf { it.expenseCents }
            val peakIndex = points.indexOfFirst { it.expenseCents == peakValue }

            fun centerX(index: Int): Float = left + slot * (index + 0.5f)

            fun barHeight(point: DailySpendPoint): Float =
                ((bottom - top) * (point.expenseCents / maxValue))
                    .coerceAtLeast(2.dp.toPx())

            val barRects = ArrayList<Rect>()
            points.forEachIndexed { index, point ->
                if (point.expenseCents <= 0L) return@forEachIndexed

                val height = barHeight(point)
                val barTop = bottom - height
                val color = if (index == peakIndex) {
                    ChartExpense
                } else {
                    ChartExpense.copy(alpha = 0.55f)
                }
                val radius = (barWidth / 2f).coerceAtMost(4.dp.toPx())
                val barLeft = centerX(index) - barWidth / 2f
                barRects += Rect(barLeft, barTop, barLeft + barWidth, bottom)

                drawRoundRect(
                    color = color,
                    topLeft = Offset(barLeft, barTop),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(x = radius, y = radius)
                )
            }

            // 金额标签：从大到小放置，保证最重要的数字一定显示
            val placed = ArrayList<Rect>()
            val gap = 0.8.dp.toPx()
            points.withIndex()
                .filter { it.value.expenseCents > 0L }
                .sortedByDescending { it.value.expenseCents }
                .forEach { (index, point) ->
                    val text = compactAmount(point.expenseCents)
                    val isPeak = index == peakIndex
                    val layout = textMeasurer.measure(
                        text = text,
                        style = if (isPeak) peakValueStyle else valueStyle,
                        maxLines = 1
                    )
                    val textWidth = layout.size.width.toFloat()
                    val textHeight = layout.size.height.toFloat()
                    if (textWidth > right - left) return@forEach

                    val barTop = bottom - barHeight(point)
                    val x = (centerX(index) - textWidth / 2f)
                        .coerceIn(left, right - textWidth)
                    val y = (barTop - textHeight - 1.dp.toPx()).coerceAtLeast(0f)
                    val rect = Rect(x - gap, y, x + textWidth + gap, y + textHeight)
                    if (placed.any { it.overlaps(rect) }) return@forEach
                    // 最高柱子的金额必须显示，允许它压在相邻柱子的顶部
                    if (!isPeak && barRects.any { it.overlaps(rect) }) return@forEach

                    placed += rect
                    drawText(layout, topLeft = Offset(x, y))
                }
        }

        val labelDays = listOf(1, 5, 10, 15, 20, 25, lastDay)
            .distinct()
            .filter { it in 1..lastDay }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            labelDays.forEach { day ->
                Text(
                    text = "${day}日",
                    style = MaterialTheme.typography.bodySmall,
                    color = labelColor
                )
            }
        }
    }
}

/** 柱顶金额的紧凑写法，尽量少占宽度：12345.67 -> 12346 / 1234 -> 1.2k / 25.5 -> 25.5 */
private fun compactAmount(cents: Long): String {
    val yuan = cents / 100.0
    return when {
        yuan >= 10000 -> "${trimEndZero(yuan / 10000)}万"
        yuan >= 1000 -> "${trimEndZero(yuan / 1000)}k"
        yuan >= 100 -> String.format(Locale.CHINA, "%.0f", yuan)
        else -> trimEndZero(yuan)
    }
}

private fun trimEndZero(value: Double): String =
    String.format(Locale.CHINA, "%.1f", value).removeSuffix(".0")

/**
 * 实心饼图：与环形图不同，中间不镂空。
 * 用于首页「本月消费构成」的紧凑展示。
 */
@Composable
fun PieChart(
    slices: List<PieSlice>,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val total = slices.sumOf { it.value }
    val gap = if (slices.size > 1) 1.2f else 0f

    Canvas(modifier = modifier) {
        val diameter = size.minDimension
        val topLeft = Offset(
            x = (size.width - diameter) / 2f,
            y = (size.height - diameter) / 2f
        )
        val arcSize = Size(diameter, diameter)

        if (total <= 0L) {
            drawCircle(
                color = trackColor,
                radius = diameter / 2f,
                center = center
            )
            return@Canvas
        }

        var startAngle = -90f
        slices.forEach { slice ->
            if (slice.value > 0L) {
                val sweep = slice.value.toFloat() / total.toFloat() * 360f
                drawArc(
                    color = slice.color,
                    startAngle = startAngle + gap / 2f,
                    sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                    useCenter = true,
                    topLeft = topLeft,
                    size = arcSize
                )
            }
            startAngle += slice.value.toFloat() / total.toFloat() * 360f
        }
    }
}
