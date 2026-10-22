package com.jianji.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jianji.app.data.model.BillType
import com.jianji.app.ui.components.SectionCard
import com.jianji.app.util.DateUtils
import com.jianji.app.util.MoneyUtils
import com.jianji.app.viewmodel.BillViewModel
import com.jianji.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.time.YearMonth

@Composable
fun BudgetScreen(
    settingsVm: SettingsViewModel,
    billVm: BillViewModel,
    onBack: () -> Unit
) {
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val bills by billVm.allBills.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    var input by rememberSaveable(settings.budgetCents) {
        mutableStateOf(MoneyUtils.plainInput(settings.budgetCents))
    }
    var error by remember { mutableStateOf<String?>(null) }

    val month = YearMonth.now()
    val spent = bills
        .filter {
            BillType.fromRaw(it.type) == BillType.EXPENSE &&
                DateUtils.yearMonth(it.date) == month
        }
        .sumOf { it.amountCents }
    val budget = settings.budgetCents
    val hasBudget = budget > 0
    val fraction = if (hasBudget) {
        (spent.toFloat() / budget.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val over = hasBudget && spent > budget
    val remaining = budget - spent

    fun save() {
        val cents = MoneyUtils.parseToCents(input)
        if (cents == null) {
            error = "请输入 0.01 ~ 99,999,999.99 之间的金额"
            return
        }
        error = null
        keyboard?.hide()
        settingsVm.setBudget(cents)
        scope.launch { snackbarHostState.showSnackbar("预算已更新") }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "月度预算",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = DateUtils.monthLabel(month),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            SectionCard {
                Text(
                    text = if (hasBudget) "本月剩余" else "还没有设置预算",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (hasBudget) {
                        if (remaining >= 0) MoneyUtils.format(remaining) else "-${MoneyUtils.format(-remaining)}"
                    } else {
                        "¥0.00"
                    },
                    style = MaterialTheme.typography.displaySmall,
                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    color = when {
                        over -> MaterialTheme.colorScheme.error
                        fraction >= 0.8f -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "已支出 ${MoneyUtils.format(spent)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "预算 ${MoneyUtils.format(budget)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (over) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "⚠️ 已超预算 ${MoneyUtils.format(spent - budget)}，接下来省着点花～",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            SectionCard {
                Text(
                    text = "设置每月预算",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "预算只统计支出，每月 1 日自动开始新周期",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        if (it.length <= 12) {
                            input = it
                            error = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("月度预算金额") },
                    prefix = { Text("¥") },
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = { save() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("保存预算")
                }
            }

            Spacer(Modifier.height(14.dp))

            SectionCard(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                Text(
                    text = "小提示",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "· 预算进度达到 80% 会变为橙色提醒\n" +
                        "· 超出预算后首页与预算页都会显示红色警示\n" +
                        "· 修改预算不会影响已经记录的历史账单",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
