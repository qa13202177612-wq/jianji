package com.jianji.app.util

import com.jianji.app.data.BillEntity
import com.jianji.app.data.model.BillType
import java.time.YearMonth

/** 某月某一天的支出合计（单位：分）。 */
data class DailySpendPoint(
    val day: Int,
    val expenseCents: Long
)

/**
 * 每日支出聚合（纯 JVM，可单元测试）。
 * 返回该月 1 日到月末的完整列表，没有支出的日期为 0。
 */
object DailyStats {

    fun dailyExpenses(
        bills: List<BillEntity>,
        month: YearMonth
    ): List<DailySpendPoint> {
        val byDay = HashMap<Int, Long>()
        for (bill in bills) {
            if (BillType.fromRaw(bill.type) != BillType.EXPENSE) continue
            val date = DateUtils.localDate(bill.date)
            if (YearMonth.from(date) != month) continue
            val day = date.dayOfMonth
            byDay[day] = (byDay[day] ?: 0L) + bill.amountCents
        }
        return (1..month.lengthOfMonth()).map { day ->
            DailySpendPoint(day = day, expenseCents = byDay[day] ?: 0L)
        }
    }
}
