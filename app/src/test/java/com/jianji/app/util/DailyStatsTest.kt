package com.jianji.app.util

import com.jianji.app.data.BillEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class DailyStatsTest {

    private fun bill(
        date: LocalDate,
        cents: Long,
        type: String = "EXPENSE"
    ): BillEntity = BillEntity(
        type = type,
        amountCents = cents,
        categoryId = "food",
        note = "",
        date = DateUtils.startOfDay(date)
    )

    @Test
    fun sumsExpensesByDayAndIgnoresIncomeAndOtherMonths() {
        val month = YearMonth.of(2026, 10)
        val bills = listOf(
            bill(LocalDate.of(2026, 10, 1), 100),
            bill(LocalDate.of(2026, 10, 1), 250),
            bill(LocalDate.of(2026, 10, 3), 500),
            bill(LocalDate.of(2026, 10, 3), 700, type = "INCOME"),
            bill(LocalDate.of(2026, 9, 30), 999)
        )

        val daily = DailyStats.dailyExpenses(bills, month)

        assertEquals(31, daily.size)
        assertEquals(350L, daily[0].expenseCents)
        assertEquals(0L, daily[1].expenseCents)
        assertEquals(500L, daily[2].expenseCents)
        assertEquals(0L, daily.last().expenseCents)
    }

    @Test
    fun returnsCompleteListForFebruaryInLeapYear() {
        val daily = DailyStats.dailyExpenses(
            bills = emptyList(),
            month = YearMonth.of(2028, 2)
        )

        assertEquals(29, daily.size)
        assertEquals(1, daily.first().day)
        assertEquals(29, daily.last().day)
        assertEquals(0L, daily.sumOf { it.expenseCents })
    }
}
