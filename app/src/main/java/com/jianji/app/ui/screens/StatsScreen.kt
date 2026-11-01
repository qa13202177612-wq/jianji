package com.jianji.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jianji.app.data.BillEntity
import com.jianji.app.data.model.BillType
import com.jianji.app.data.model.Categories
import com.jianji.app.util.DateUtils
import com.jianji.app.util.Merchants
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
    /** 被点击查看明细的「日」;null = 未打开 */
    var detailDay by remember { mutableStateOf<Int?>(null) }
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
                    DailySpendChart(
                        points = stats.dailyExpenses,
                        onDayClick = { day -> detailDay = day }
                    )

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
                                .clickable { detailDay = point.day }
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

            // ---------------- 商家消费排行 ----------------
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "商家排行",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "本月支出 TOP${maxOf(stats.merchantRanking.size, 1)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (stats.merchantRanking.isEmpty()) {
                    Text(
                        text = "本月暂无可归类的支出",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val maxCents = stats.merchantRanking.first().cents
                    stats.merchantRanking.forEachIndexed { index, item ->
                        MerchantRankRow(
                            rank = index + 1,
                            name = item.name,
                            count = item.count,
                            cents = item.cents,
                            fraction = if (maxCents > 0) item.cents.toFloat() / maxCents else 0f
                        )
                        Spacer(Modifier.height(10.dp))
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

        // 点柱子/日列表 → 当日明细弹窗
        detailDay?.let { day ->
            val dayBills = stats.expenseBills
                .filter { DateUtils.localDate(it.date).dayOfMonth == day }
                .sortedByDescending { it.amountCents }
            DayDetailDialog(
                month = month,
                day = day,
                bills = dayBills,
                onDismiss = { detailDay = null }
            )
        }
    }
}

@Composable
private fun DayDetailDialog(
    month: YearMonth,
    day: Int,
    bills: List<BillEntity>,
    onDismiss: () -> Unit
) {
    val total = bills.sumOf { it.amountCents }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "${month.monthValue}月${day}日 · 消费明细",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (bills.isEmpty()) "当天没有支出"
                            else "共 ${bills.size} 笔 · 合计 ${MoneyUtils.format(total)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }

                Spacer(Modifier.height(12.dp))

                if (bills.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(88.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🗓 当天没有支出记录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(bills) { bill ->
                            BillDetailRow(bill)
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BillDetailRow(bill: BillEntity) {
    val type = BillType.fromRaw(bill.type)
    val category = Categories.safeById(bill.categoryId, type)
    val merchant = Merchants.resolve(bill)
    val sourceTag = when (bill.source) {
        BillEntity.SOURCE_SMS -> "短信"
        BillEntity.SOURCE_SCREEN -> "识屏"
        else -> "手动"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = category.emoji, fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (merchant == Merchants.UNKNOWN) category.name else merchant,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$sourceTag · ${category.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = MoneyUtils.format(bill.amountCents),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (type == BillType.EXPENSE) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun MerchantRankRow(
    rank: Int,
    name: String,
    count: Int,
    cents: Long,
    fraction: Float
) {
    val rankBg = when (rank) {
        1 -> Color(0xFFE6A23C)
        2 -> Color(0xFF98A2B3)
        3 -> Color(0xFFBE7B5A)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val rankFg = if (rank <= 3) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(color = rankBg, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = rankFg
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(3.dp)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0.03f, 1f))
                        .height(6.dp)
                        .background(color = ChartExpense, shape = RoundedCornerShape(3.dp))
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = MoneyUtils.format(cents),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${count}笔",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
