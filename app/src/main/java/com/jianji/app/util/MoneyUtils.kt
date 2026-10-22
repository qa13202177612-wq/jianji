package com.jianji.app.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

object MoneyUtils {

    private val MAX_AMOUNT = BigDecimal("99999999.99")

    fun format(cents: Long): String = "¥" + grouped(cents)

    fun grouped(cents: Long): String {
        val value = BigDecimal.valueOf(cents).movePointLeft(2)
        return NumberFormat.getNumberInstance(Locale.CHINA).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(value)
    }

    /** CSV 中使用，不加千分位、不带货币符号 */
    fun plainCents(cents: Long): String =
        BigDecimal.valueOf(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString()

    /** 输入框回显，例如 12.00 -> "12.00"，12.50 -> "12.50" */
    fun plainInput(cents: Long): String = plainCents(cents)

    /**
     * 把用户输入的元转成分。
     * 任何非法输入（空、负数、0、超过上限、多个小数点）都返回 null，由调用方提示。
     */
    fun parseToCents(input: String): Long? {
        val cleaned = input.trim()
            .replace(",", "")
            .replace("，", "")
            .replace("¥", "")
            .replace("￥", "")
            .replace("元", "")
            .replace(" ", "")
        if (cleaned.isEmpty()) return null
        if (cleaned.count { it == '.' } > 1) return null
        val value = cleaned.toBigDecimalOrNull() ?: return null
        if (value <= BigDecimal.ZERO) return null
        if (value > MAX_AMOUNT) return null
        if (value.scale() > 2) return null
        return try {
            value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (e: ArithmeticException) {
            null
        }
    }

    fun percent(part: Long, total: Long): Int {
        if (total <= 0L) return 0
        return ((part.toDouble() / total.toDouble()) * 100).toInt().coerceIn(0, 100)
    }
}
