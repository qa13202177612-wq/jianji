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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jianji.app.data.AppSettings
import com.jianji.app.data.model.BillType
import com.jianji.app.data.model.Categories
import com.jianji.app.ui.components.CategoryGrid
import com.jianji.app.ui.components.JianJiDatePickerDialog
import com.jianji.app.ui.components.PieChart
import com.jianji.app.ui.components.PieSlice
import com.jianji.app.ui.components.SectionCard
import com.jianji.app.ui.components.ScreenHeader
import com.jianji.app.ui.components.TypeToggle
import com.jianji.app.ui.components.rememberSmsPermissionUi
import com.jianji.app.util.DateUtils
import com.jianji.app.util.MoneyUtils
import com.jianji.app.viewmodel.BillViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun HomeScreen(
    billVm: BillViewModel,
    settings: AppSettings,
    onOpenBudget: () -> Unit
) {
    val bills by billVm.allBills.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val smsPermission = rememberSmsPermissionUi()

    var typeName by rememberSaveable { mutableStateOf(BillType.EXPENSE.name) }
    val type = BillType.fromRaw(typeName)
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf(Categories.expenses.first().id) }
    var dateMillis by rememberSaveable {
        mutableStateOf(DateUtils.startOfDay(LocalDate.now()))
    }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(typeName) {
        if (Categories.byId(categoryId)?.type != type) {
            categoryId = Categories.fallback(type).id
        }
    }

    LaunchedEffect(Unit) {
        billVm.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val month = YearMonth.now()
    val monthExpenseBills = bills.filter {
        BillType.fromRaw(it.type) == BillType.EXPENSE &&
            DateUtils.yearMonth(it.date) == month
    }
    val monthExpense = monthExpenseBills.sumOf { it.amountCents }
    val expenseSlices = monthExpenseBills
        .groupBy { it.categoryId }
        .map { (categoryId, items) ->
            val category = Categories.safeById(categoryId, BillType.EXPENSE)
            PieSlice(
                label = category.name,
                value = items.sumOf { it.amountCents },
                color = Color(category.color)
            )
        }
        .sortedByDescending { it.value }
    val budget = settings.budgetCents

    fun saveBill() {
        val cents = MoneyUtils.parseToCents(amount)
        if (cents == null) {
            scope.launch { snackbarHostState.showSnackbar("请输入 0.01 ~ 99,999,999.99 之间的金额") }
            return
        }
        val category = Categories.safeById(categoryId, type)
        billVm.addBill(
            type = type,
            amountCents = cents,
            categoryId = category.id,
            note = note.trim(),
            dateMillis = DateUtils.startOfDay(dateMillis.let { DateUtils.localDate(it) })
        )
        amount = ""
        note = ""
        if (budget > 0 && monthExpense + cents > budget) {
            scope.launch { snackbarHostState.showSnackbar("本月预算已超支，注意控制开支") }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 20.dp)
        ) {
            ScreenHeader(
                title = "简记",
                subtitle = DateUtils.fullDate(LocalDate.now())
            )

            Spacer(Modifier.height(16.dp))

            BudgetCard(
                budgetCents = budget,
                spentCents = monthExpense,
                onOpenBudget = onOpenBudget
            )

            Spacer(Modifier.height(14.dp))

            HomeExpensePieCard(
                slices = expenseSlices,
                totalCents = monthExpense,
                month = month
            )

            if (settings.autoSms && !smsPermission.granted) {
                Spacer(Modifier.height(12.dp))
                SmsHintCard(onEnable = smsPermission.request)
            }

            Spacer(Modifier.height(14.dp))

            SectionCard {
                TypeToggle(type = type, onChange = { typeName = it.name })

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = amount,
                    onValueChange = { value ->
                        if (value.length <= 12) amount = value
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("金额") },
                    prefix = { Text("¥", style = MaterialTheme.typography.headlineSmall) },
                    placeholder = { Text("0.00") },
                    textStyle = MaterialTheme.typography.headlineSmall,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                Spacer(Modifier.height(18.dp))

                Text(
                    text = "选择分类",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                CategoryGrid(
                    categories = Categories.forType(type),
                    selectedId = categoryId,
                    onSelect = { categoryId = it.id }
                )

                Spacer(Modifier.height(18.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 100) note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备注（可选）") },
                    placeholder = { Text("例如：和朋友吃午饭") },
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                Surface(
                    onClick = { showDatePicker = true },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "📅", modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = DateUtils.fullDate(DateUtils.localDate(dateMillis)),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "修改",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { saveBill() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = "保存这一笔",
                        style = MaterialTheme.typography.titleMedium
                    )
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

    if (showDatePicker) {
        JianJiDatePickerDialog(
            initialDate = DateUtils.localDate(dateMillis),
            onDismiss = { showDatePicker = false },
            onConfirm = {
                dateMillis = DateUtils.startOfDay(it)
                showDatePicker = false
            }
        )
    }
}

@Composable
private fun BudgetCard(
    budgetCents: Long,
    spentCents: Long,
    onOpenBudget: () -> Unit
) {
    val hasBudget = budgetCents > 0
    val fraction = if (hasBudget) {
        (spentCents.toFloat() / budgetCents.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val over = spentCents > budgetCents && hasBudget
    val progressColor = when {
        over -> MaterialTheme.colorScheme.error
        fraction >= 0.8f -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }

    SectionCard(modifier = Modifier.clickable(onClick = onOpenBudget)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "本月预算",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                if (hasBudget) {
                    Text(
                        text = if (over) {
                            "超支 ${MoneyUtils.format(spentCents - budgetCents)}"
                        } else {
                            "剩余 ${MoneyUtils.format(budgetCents - spentCents)}"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        text = "点击设置月度预算",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = if (hasBudget) {
                    "已用 ${(fraction * 100).toInt()}%"
                } else {
                    "去设置"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (hasBudget) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "已支出 ${MoneyUtils.format(spentCents)} / 预算 ${MoneyUtils.format(budgetCents)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 日均可用 = 剩余预算 ÷ 剩余天数(含今天)
            val daysLeft = java.time.YearMonth.now().lengthOfMonth() -
                    java.time.LocalDate.now().dayOfMonth + 1
            Spacer(Modifier.height(6.dp))
            if (over) {
                Text(
                    text = "预算已花完,剩下 $daysLeft 天省着点 ✌️",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                val daily = Math.round((budgetCents - spentCents).toDouble() / daysLeft)
                Text(
                    text = "剩 $daysLeft 天 · 日均可用 ${MoneyUtils.format(daily)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SmsHintCard(onEnable: () -> Unit) {
    SectionCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = Color.White.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "📩", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "开启短信自动记账",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "自动识别银行 / 支付短信，不用手动输入",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
            TextButton(onClick = onEnable) {
                Text("开启")
            }
        }
    }
}

/**
 * 首页的「本月消费构成」：实心饼图 + 前几类占比，点开统计页可看完整分类。
 */
@Composable
private fun HomeExpensePieCard(
    slices: List<PieSlice>,
    totalCents: Long,
    month: YearMonth
) {
    val legendSlices = if (slices.size > 4) {
        slices.take(3) + PieSlice(
            label = "其他 ${slices.size - 3} 类",
            value = slices.drop(3).sumOf { it.value },
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        )
    } else {
        slices
    }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "本月消费构成",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "共 ${MoneyUtils.format(totalCents)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(12.dp))

        if (totalCents <= 0L) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${month.monthValue}月还没有支出，记一笔就能看到消费构成",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PieChart(
                    slices = slices,
                    modifier = Modifier.size(108.dp)
                )
                Spacer(Modifier.size(18.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    legendSlices.forEach { slice ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(color = slice.color, shape = CircleShape)
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                text = slice.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${MoneyUtils.percent(slice.value, totalCents)}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
