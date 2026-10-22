package com.jianji.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jianji.app.data.BillEntity
import com.jianji.app.data.model.BillType
import com.jianji.app.data.model.Categories
import com.jianji.app.ui.components.EditBillDialog
import com.jianji.app.ui.components.EmptyState
import com.jianji.app.ui.components.MonthSelector
import com.jianji.app.ui.components.ScreenHeader
import com.jianji.app.ui.components.SectionCard
import com.jianji.app.util.DateUtils
import com.jianji.app.util.MoneyUtils
import com.jianji.app.viewmodel.BillViewModel
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun BillsScreen(billVm: BillViewModel) {
    val bills by billVm.filteredBills.collectAsStateWithLifecycle()
    val month by billVm.selectedMonth.collectAsStateWithLifecycle()
    val typeFilter by billVm.typeFilter.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var editing by remember { mutableStateOf<BillEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<BillEntity?>(null) }

    LaunchedEffect(Unit) {
        billVm.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    val expenseTotal = bills
        .filter { BillType.fromRaw(it.type) == BillType.EXPENSE }
        .sumOf { it.amountCents }
    val incomeTotal = bills
        .filter { BillType.fromRaw(it.type) == BillType.INCOME }
        .sumOf { it.amountCents }

    val grouped = bills
        .groupBy { DateUtils.localDate(it.date) }
        .entries
        .sortedByDescending { it.key }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "header") {
                ScreenHeader(
                    title = "账单",
                    subtitle = "共 ${bills.size} 笔 · 点击账单可修改"
                )
            }

            item(key = "filter") {
                SectionCard {
                    MonthSelector(
                        month = month,
                        onPrevious = { billVm.previousMonth() },
                        onNext = { billVm.nextMonth() }
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = typeFilter == null,
                            onClick = { billVm.selectType(null) },
                            label = { Text("全部") }
                        )
                        FilterChip(
                            selected = typeFilter == BillType.EXPENSE,
                            onClick = { billVm.selectType(BillType.EXPENSE) },
                            label = { Text("支出") }
                        )
                        FilterChip(
                            selected = typeFilter == BillType.INCOME,
                            onClick = { billVm.selectType(BillType.INCOME) },
                            label = { Text("收入") }
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "支出",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = MoneyUtils.format(expenseTotal),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "收入",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = MoneyUtils.format(incomeTotal),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "结余",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = MoneyUtils.format(incomeTotal - expenseTotal),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            if (bills.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        emoji = "🧾",
                        title = "本月还没有账单",
                        message = "回到「记账」页记下第一笔，或等待短信自动记账"
                    )
                }
            } else {
                grouped.forEach { (date, dayBills) ->
                    item(key = "day-${date}") {
                        DayHeader(date = date, bills = dayBills)
                    }
                    items(items = dayBills, key = { it.id }) { bill ->
                        BillRow(
                            bill = bill,
                            onClick = { editing = bill },
                            onDelete = { pendingDelete = bill }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    editing?.let { bill ->
        EditBillDialog(
            bill = bill,
            onDismiss = { editing = null },
            onSave = { updated ->
                billVm.updateBill(updated)
                editing = null
            }
        )
    }

    pendingDelete?.let { bill ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这笔账单？") },
            text = {
                Text(
                    "${Categories.safeById(bill.categoryId, BillType.fromRaw(bill.type)).name} " +
                        MoneyUtils.format(bill.amountCents) + "，删除后不可恢复。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        billVm.deleteBill(bill.id)
                        pendingDelete = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun DayHeader(date: LocalDate, bills: List<BillEntity>) {
    val expense = bills.filter { BillType.fromRaw(it.type) == BillType.EXPENSE }
        .sumOf { it.amountCents }
    val income = bills.filter { BillType.fromRaw(it.type) == BillType.INCOME }
        .sumOf { it.amountCents }
    val label = if (date == LocalDate.now()) "今天 · ${DateUtils.dayLabel(date)}" else DateUtils.dayLabel(date)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (expense > 0) {
            Text(
                text = "支出 ${MoneyUtils.format(expense)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (income > 0) {
            Spacer(Modifier.size(8.dp))
            Text(
                text = "收入 ${MoneyUtils.format(income)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun BillRow(
    bill: BillEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val type = BillType.fromRaw(bill.type)
    val category = Categories.safeById(bill.categoryId, type)
    val isIncome = type == BillType.INCOME
    val sign = if (isIncome) "+" else "-"
    val sourceLabel = when (bill.source) {
        BillEntity.SOURCE_SMS -> "短信"
        BillEntity.SOURCE_SCREEN -> "屏幕"
        else -> "手动"
    }
    val sourceNote = when (bill.source) {
        BillEntity.SOURCE_SMS -> "短信自动记账"
        BillEntity.SOURCE_SCREEN -> "屏幕识别记账"
        else -> "手动记账"
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = androidx.compose.ui.graphics.Color(category.color).copy(alpha = 0.16f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = category.emoji, style = MaterialTheme.typography.titleLarge)
            }

            Spacer(Modifier.size(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = bill.note.ifBlank { sourceNote },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$sign${MoneyUtils.grouped(bill.amountCents)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isIncome) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = sourceLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "删除账单",
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
