package com.jianji.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jianji.app.JianJiApp
import com.jianji.app.data.BillEntity
import com.jianji.app.data.model.BillType
import com.jianji.app.util.DateUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

class BillViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as JianJiApp).repository

    val allBills: StateFlow<List<BillEntity>> = repository.bills.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    private val _typeFilter = MutableStateFlow<BillType?>(null)
    val typeFilter: StateFlow<BillType?> = _typeFilter.asStateFlow()

    val filteredBills: StateFlow<List<BillEntity>> = combine(
        repository.bills,
        _selectedMonth,
        _typeFilter
    ) { bills, month, type ->
        bills.filter { bill ->
            DateUtils.yearMonth(bill.date) == month &&
                (type == null || bill.type == type.name)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    fun selectMonth(month: YearMonth) {
        _selectedMonth.value = month
    }

    fun previousMonth() {
        _selectedMonth.value = _selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        val next = _selectedMonth.value.plusMonths(1)
        if (!next.isAfter(YearMonth.now())) {
            _selectedMonth.value = next
        }
    }

    fun selectType(type: BillType?) {
        _typeFilter.value = type
    }

    fun addBill(
        type: BillType,
        amountCents: Long,
        categoryId: String,
        note: String,
        dateMillis: Long
    ) {
        viewModelScope.launch {
            try {
                repository.add(
                    BillEntity(
                        type = type.name,
                        amountCents = amountCents,
                        categoryId = categoryId,
                        note = note,
                        date = dateMillis,
                        source = BillEntity.SOURCE_MANUAL
                    )
                )
                _messages.emit("已记一笔")
            } catch (t: Throwable) {
                _messages.emit("保存失败，请重试")
            }
        }
    }

    fun updateBill(bill: BillEntity) {
        viewModelScope.launch {
            try {
                repository.update(bill)
                _messages.emit("已保存修改")
            } catch (t: Throwable) {
                _messages.emit("修改失败，请重试")
            }
        }
    }

    fun deleteBill(id: Long) {
        viewModelScope.launch {
            try {
                repository.delete(id)
                _messages.emit("已删除")
            } catch (t: Throwable) {
                _messages.emit("删除失败，请重试")
            }
        }
    }

    suspend fun snapshot(): List<BillEntity> = repository.all()
}
