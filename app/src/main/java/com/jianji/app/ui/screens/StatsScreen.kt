package com.jianji.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jianji.app.data.model.BillType
import com.jianji.app.ui.components.DailySpendChart
import com.jianji.app.ui.components.DonutChart
import com.jianji.app.ui.components.MonthSelector
import com.jianji.app.ui.components.PieSlice
import com.jianji.app.ui.components.ScreenHeader
import com.jianji.app.ui.components.SectionCard
import com.jianji.app.ui.components.TrendChart
import com.jianji.app.ui.theme.ChartExpense
import com.jianji.app.ui.theme.ChartIncome
import com.jianji.app.util.MoneyUtils
import com.jianji.app.viewmodel.CategorySlice
import com.jianji.app.viewmodel.StatsViewModel
import java.time.YearMonth

@Composable
fun StatsScreen(statsVm: StatsViewModel) {
    val stats by statsVm.stats.collectAsStateWithLifecycle()
    val month by statsVm.month.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var pieType by remember { mutableStateOf(BillType.EXPENSE) }
    val slices: List<CategorySlice> = if (pieType == BillType.EXPENSE) {
        stats.expenseSlices
    } else {
        stats.incomeSlices
    }
    val sliceTotal = slices.sumOf { it.cents }
    val pieSlices = slices.map { slice ->
        PieSlice(
            label = slice.category.name,
            value = slice.cents,
            color = Color(slice.category.color)
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 24.dp)
        ) {
            ScreenHeader(title = "统计", subtitle = "看清每一笔钱的去向")

            Spacer(Modifier.height(12.dp))

            SectionCard {
                MonthSelector(
                    month = month,
                    onPrevious = { statsVm.previousMonth() },
                    onNext = { statsVm.nextMonth() }
                )

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryCard(
                        title = "总收入",
                        amount = stats.incomeCents,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCard(
                        title = "总支出",
                        amount = stats.expenseCents,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCard(
                        title = "结余",
                        amount = stats.balanceCents,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "每日支出",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${month.monthValue}月 · 仅支出（元）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(14.dp))

                if (stats.expenseCents <= 0L) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "本月暂无支出",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    DailySpendChart(points = stats.dailyExpenses)

                    val daysWithSpend = stats.dailyExpenses.filter { it.expenseCents > 0L }
                    val peak = daysWithSpend.maxByOrNull { it.expenseCents }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "有支出 ${daysWithSpend.size} 天",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (peak != null) {
                            Text(
                                text = "最高 ${peak.day}日 " +
                                    MoneyUtils.format(peak.expenseCents),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    daysWithSpend.forEach { point ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${month.monthValue}月${point.day}日",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = MoneyUtils.format(point.expenseCents),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "分类占比",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = pieType == BillType.EXPENSE,
                        onClick = { pieType = BillType.EXPENSE },
                        label = { Text("支出") }
                    )
                    Spacer(Modifier.size(6.dp))
                    FilterChip(
                        selected = pieType == BillType.INCOME,
                        onClick = { pieType = BillType.INCOME },
                        label = { Text("收入") }
                    )
                }

                Spacer(Modifier.height(14.dp))

                if (sliceTotal <= 0L) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        DonutChart(
                            slices = emptyList(),
                            modifier = Modifier.size(170.dp),
                            center = {
                                Text(
                                    text = "暂无数据",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        DonutChart(
                            slices = pieSlices,
                            modifier = Modifier.size(190.dp),
                            center = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = if (pieType == BillType.EXPENSE) "总支出" else "总收入",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = MoneyUtils.format(sliceTotal),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    slices.forEach { slice ->
                        LegendRow(
                            name = "${slice.category.emoji} ${slice.category.name}",
                            color = Color(slice.category.color),
                            amount = slice.cents,
                            total = sliceTotal
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "近 6 个月趋势",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    LegendDot(color = ChartIncome, label = "收入")
                    Spacer(Modifier.size(10.dp))
                    LegendDot(color = ChartExpense, label = "支出")
                }

                Spacer(Modifier.height(14.dp))

                TrendChart(points = stats.trend)
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

@Composable
private fun SummaryCard(
    title: String,
    amount: Long,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = MoneyUtils.grouped(amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun LegendRow(
    name: String,
    color: Color,
    amount: Long,
    total: Long
) {
    val percent = MoneyUtils.percent(amount, total)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color = color, shape = CircleShape)
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.size(width = 42.dp, height = 18.dp)
        )
        Text(
            text = MoneyUtils.format(amount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = color, shape = CircleShape)
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
