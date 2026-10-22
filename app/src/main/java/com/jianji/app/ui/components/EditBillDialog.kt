package com.jianji.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jianji.app.data.BillEntity
import com.jianji.app.data.model.BillType
import com.jianji.app.data.model.Categories
import com.jianji.app.util.DateUtils
import com.jianji.app.util.MoneyUtils
import java.time.LocalDate

@Composable
fun EditBillDialog(
    bill: BillEntity,
    onDismiss: () -> Unit,
    onSave: (BillEntity) -> Unit
) {
    var type by remember { mutableStateOf(BillType.fromRaw(bill.type)) }
    var amount by remember { mutableStateOf(MoneyUtils.plainInput(bill.amountCents)) }
    var note by remember { mutableStateOf(bill.note) }
    var categoryId by remember {
        mutableStateOf(Categories.safeById(bill.categoryId, BillType.fromRaw(bill.type)).id)
    }
    var date by remember { mutableStateOf(DateUtils.localDate(bill.date)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(type) {
        val current = Categories.byId(categoryId)
        if (current == null || current.type != type) {
            categoryId = Categories.fallback(type).id
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改账单") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                TypeToggle(type = type, onChange = { type = it })

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { value ->
                        if (value.length <= 12) {
                            amount = value
                            error = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("金额") },
                    prefix = { Text("¥") },
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "分类",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Categories.forType(type), key = { it.id }) { category ->
                        FilterChip(
                            selected = category.id == categoryId,
                            onClick = { categoryId = category.id },
                            label = { Text("${category.emoji} ${category.name}") }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 100) note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备注") },
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "日期：${DateUtils.fullDate(date)}",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cents = MoneyUtils.parseToCents(amount)
                    if (cents == null) {
                        error = "请输入 0.01 ~ 99,999,999.99 之间的金额"
                    } else {
                        onSave(
                            bill.copy(
                                type = type.name,
                                amountCents = cents,
                                categoryId = categoryId,
                                note = note.trim(),
                                date = DateUtils.startOfDay(date)
                            )
                        )
                    }
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )

    if (showDatePicker) {
        JianJiDatePickerDialog(
            initialDate = date,
            onDismiss = { showDatePicker = false },
            onConfirm = { picked: LocalDate ->
                date = picked
                showDatePicker = false
            }
        )
    }
}
