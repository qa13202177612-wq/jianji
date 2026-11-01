package com.jianji.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jianji.app.JianJiApp
import com.jianji.app.data.BillEntity
import com.jianji.app.data.model.BillType
import com.jianji.app.data.model.Category
import com.jianji.app.data.model.Categories
import com.jianji.app.util.DailySpendPoint
import com.jianji.app.util.DailyStats
import com.jianji.app.util.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

data class CategorySlice(
    val category: Category,
    val cents: Long
)

data class TrendPoint(
    val month: YearMonth,
    val incomeCents: Long,
    val expenseCents: Long
)

/** 商家消费排行的一行:金额 + 笔数 */
data class MerchantSpend(
    val name: String,
    val cents: Long,
    val count: Int
)

data class MonthStats(
    val month: YearMonth = YearMonth.now(),
    val incomeCents: Long = 0L,
    val expenseCents: Long = 0L,
    val expenseSlices: List<CategorySlice> = emptyList(),
    val incomeSlices: List<CategorySlice> = emptyList(),
    val trend: List<TrendPoint> = emptyList(),
    val dailyExpenses: List<DailySpendPoint> = emptyList(),
    /** 选中月份的全部支出账单,供柱状图点击后的「当日明细」弹窗过滤使用 */
    val expenseBills: List<BillEntity> = emptyList(),
    /** 商家消费排行(已按金额降序,最多 10 名) */
    val merchantRanking: List<MerchantSpend> = emptyList()
) {
    val balanceCents: Long get() = incomeCents - expenseCents
}

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as JianJiApp).repository

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    val stats: StateFlow<MonthStats> = combine(repository.bills, _month) { bills, month ->
        buildStats(bills, month)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MonthStats()
    )

    fun previousMonth() {
        _month.value = _month.value.minusMonths(1)
    }

    fun nextMonth() {
        val next = _month.value.plusMonths(1)
        if (!next.isAfter(YearMonth.now())) {
            _month.value = next
        }
    }

    private fun buildStats(bills: List<BillEntity>, month: YearMonth): MonthStats {
        val monthBills = bills.filter { DateUtils.yearMonth(it.date) == month }

        val expenseBills = monthBills.filter { BillType.fromRaw(it.type) == BillType.EXPENSE }
        val incomeBills = monthBills.filter { BillType.fromRaw(it.type) == BillType.INCOME }

        val expenseSlices = slicesOf(expenseBills, BillType.EXPENSE)
        val incomeSlices = slicesOf(incomeBills, BillType.INCOME)

        val trend = (5 downTo 0).map { offset ->
            val m = month.minusMonths(offset.toLong())
            val list = bills.filter { DateUtils.yearMonth(it.date) == m }
            TrendPoint(
                month = m,
                incomeCents = list.filter { BillType.fromRaw(it.type) == BillType.INCOME }
                    .sumOf { it.amountCents },
                expenseCents = list.filter { BillType.fromRaw(it.type) == BillType.EXPENSE }
                    .sumOf { it.amountCents }
            )
        }

        val merchantRanking = expenseBills
            .groupBy { com.jianji.app.util.Merchants.resolve(it) }
            .map { (name, items) ->
                MerchantSpend(
                    name = name,
                    cents = items.sumOf { it.amountCents },
                    count = items.size
                )
            }
            .sortedByDescending { it.cents }
            .take(10)

        return MonthStats(
            month = month,
            incomeCents = incomeBills.sumOf { it.amountCents },
            expenseCents = expenseBills.sumOf { it.amountCents },
            expenseSlices = expenseSlices,
            incomeSlices = incomeSlices,
            trend = trend,
            dailyExpenses = DailyStats.dailyExpenses(monthBills, month),
            expenseBills = expenseBills,
            merchantRanking = merchantRanking
        )
    }

    private fun slicesOf(bills: List<BillEntity>, type: BillType): List<CategorySlice> {
        return bills.groupBy { it.categoryId }
            .map { (categoryId, items) ->
                CategorySlice(
                    category = Categories.safeById(categoryId, type),
                    cents = items.sumOf { it.amountCents }
                )
            }
            .sortedByDescending { it.cents }
    }
}
