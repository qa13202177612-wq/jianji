package com.jianji.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateUtils {

    private val fullFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)
    private val dayFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
    private val csvFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA)

    fun localDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    /** 当天 00:00 的时间戳，保证同一天的账单聚合稳定 */
    fun startOfDay(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun yearMonth(millis: Long): YearMonth = YearMonth.from(localDate(millis))

    fun monthLabel(month: YearMonth): String = "${month.year}年${month.monthValue}月"

    fun shortMonthLabel(month: YearMonth): String = "${month.monthValue}月"

    fun dayLabel(date: LocalDate): String = date.format(dayFormatter)

    fun fullDate(date: LocalDate): String = date.format(fullFormatter)

    fun csvDate(date: LocalDate): String = date.format(csvFormatter)

    fun isCurrentMonth(month: YearMonth): Boolean = month == YearMonth.now()
}
