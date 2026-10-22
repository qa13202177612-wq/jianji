package com.jianji.app.sms

import com.jianji.app.data.model.BillType
import com.jianji.app.data.model.CategoryIds
import com.jianji.app.util.DateUtils
import com.jianji.app.util.MoneyUtils
import java.security.MessageDigest

data class ParsedSms(
    val amountCents: Long,
    val type: BillType,
    val categoryId: String,
    val note: String,
    val dateMillis: Long
)

/**
 * 短信解析器。
 *
 * 设计原则：
 * 1. 只识别包含明确「收支动作 + 金额」的短信，验证码、营销、账单查询等直接忽略；
 * 2. 任何正则/数字异常都被捕获，返回 null，绝不让广播接收器崩溃；
 * 3. 解析结果只作为「草稿账单」，用户可在账单页修改或删除。
 */
object SmsParser {

    /** 明确不属于交易的通知 */
    private val EXCLUDE_WORDS = listOf(
        "验证码", "校验码", "动态码", "动态密码", "随机码", "短信密码",
        "注册", "激活", "积分兑换", "积分商城", "账单查询",
        "额度调整", "办理分期", "退订", "回复TD", "下载APP"
    )

    private val EXPENSE_WORDS = listOf(
        "消费", "支出", "支付", "付款", "扣款", "转出", "取现", "取款", "缴费", "还款"
    )

    private val INCOME_WORDS = listOf(
        "收入", "入账", "转入", "退款", "到账", "工资", "奖金", "分红", "利息", "红包", "收款"
    )

    /** 关键词在前、金额在后，且带「元」，最可靠 */
    private val amountAfterKeyword = Regex(
        """(?:消费金额|交易金额|消费|支出|支付|付款|扣款|转出|取现|取款|缴费|还款|入账|收入|转入|退款|到账|金额)[^0-9]{0,12}(?:人民币|RMB|CNY|¥|￥)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*元""",
        RegexOption.IGNORE_CASE
    )

    /** 只有「人民币/¥ + 金额 + 元」 */
    private val currencyAmount = Regex(
        """(?:人民币|RMB|CNY|¥|￥)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*元""",
        RegexOption.IGNORE_CASE
    )

    /** 金额在前、收支动作在后：如「25.00元的支出」 */
    private val amountBeforeKeyword = Regex(
        """([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*元[^0-9]{0,8}(?:支出|消费|收入|入账|到账)"""
    )

    /** 兜底：关键词后跟金额但没有「元」 */
    private val keywordNoYuan = Regex(
        """(?:消费|支出|支付|付款|扣款|转出|取现|取款|收入|入账|转入|退款|到账)[^0-9]{0,12}(?:人民币|RMB|CNY|¥|￥)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    private val AMOUNT_PATTERNS = listOf(
        amountAfterKeyword,
        currencyAmount,
        amountBeforeKeyword,
        keywordNoYuan
    )

    fun parse(body: String, sender: String?, timestamp: Long): ParsedSms? {
        return try {
            val text = body.trim()
            if (text.length < 4) return null
            if (EXCLUDE_WORDS.any { text.contains(it, ignoreCase = true) }) return null

            val type = detectType(text) ?: return null

            // 必须像一条含金额的金融短信，避免把普通通知误记成账单
            if (!looksFinancial(text)) return null

            val amountCents = extractAmount(text) ?: return null
            val categoryId = guessCategory(text, type)
            val note = buildNote(text, sender)
            val dateMillis = DateUtils.startOfDay(DateUtils.localDate(timestamp))

            ParsedSms(
                amountCents = amountCents,
                type = type,
                categoryId = categoryId,
                note = note,
                dateMillis = dateMillis
            )
        } catch (t: Throwable) {
            // 解析失败静默返回，短信接收器会继续处理下一条
            null
        }
    }

    private fun detectType(text: String): BillType? {
        // 退款优先判定为收入，即使短信里同时出现「支付」
        if (text.contains("退款") || text.contains("退货")) return BillType.INCOME

        // 「支付宝」本身含「支付」二字，先替换掉再判断支出，避免所有支付宝短信都被当成支出
        val normalized = text.replace("支付宝", "ALIPAY")
        val hasExpense = EXPENSE_WORDS.any { normalized.contains(it) }
        val hasIncome = INCOME_WORDS.any { text.contains(it) }

        return when {
            hasExpense && !hasIncome -> BillType.EXPENSE
            hasIncome && !hasExpense -> BillType.INCOME
            hasExpense && hasIncome -> {
                // 同时命中时，以明确的入账动词优先，避免「交易收入」被误判
                if (text.contains("收入") || text.contains("入账") ||
                    text.contains("到账") || text.contains("转入")
                ) {
                    BillType.INCOME
                } else {
                    BillType.EXPENSE
                }
            }
            else -> null
        }
    }

    private fun looksFinancial(text: String): Boolean {
        val hasCurrency = text.contains("元") ||
            text.contains("人民币") ||
            text.contains("¥") ||
            text.contains("￥") ||
            text.contains("RMB", ignoreCase = true)
        return hasCurrency
    }

    private fun extractAmount(text: String): Long? {
        for (pattern in AMOUNT_PATTERNS) {
            val matches = pattern.findAll(text)
            for (match in matches) {
                val raw = match.groupValues.getOrNull(1).orEmpty()
                if (raw.isBlank()) continue
                val cents = MoneyUtils.parseToCents(raw) ?: continue
                // 金额上限 9999 万元，过滤掉把日期/卡号误识别成金额的极端情况
                if (cents in 1L..9_999_999_999L) {
                    return cents
                }
            }
        }
        return null
    }

    private fun guessCategory(text: String, type: BillType): String {
        if (type == BillType.INCOME) {
            return when {
                containsAny(text, "工资", "薪资", "代发", "薪酬") -> CategoryIds.SALARY
                containsAny(text, "退款", "退货") -> CategoryIds.REFUND
                containsAny(text, "奖金", "年终奖", "奖励") -> CategoryIds.BONUS
                containsAny(text, "利息", "理财", "分红", "收益", "基金") -> CategoryIds.INVESTMENT
                containsAny(text, "兼职", "稿费", "劳务") -> CategoryIds.PART_TIME
                else -> CategoryIds.OTHER_INCOME
            }
        }
        return when {
            containsAny(text, "医院", "药", "医疗", "挂号", "体检") -> CategoryIds.MEDICAL
            containsAny(text, "地铁", "公交", "滴滴", "打车", "加油", "停车", "高铁", "火车", "机票", "航空") -> CategoryIds.TRANSPORT
            containsAny(text, "淘宝", "天猫", "京东", "拼多多", "商城", "购物", "超市", "便利店", "商场") -> CategoryIds.SHOPPING
            containsAny(text, "电影", "游戏", "娱乐", "KTV", "视频", "会员") -> CategoryIds.FUN
            containsAny(text, "房租", "水费", "电费", "燃气", "物业", "家居") -> CategoryIds.HOME
            containsAny(text, "书店", "图书", "课程", "培训", "学费", "教育") -> CategoryIds.STUDY
            containsAny(text, "话费", "流量", "宽带", "通信", "手机") -> CategoryIds.COMMUNICATION
            containsAny(text, "宠物", "猫粮", "狗粮", "兽医") -> CategoryIds.PET
            containsAny(text, "餐", "饭", "外卖", "美团", "饿了么", "咖啡", "奶茶", "小吃", "烧烤") -> CategoryIds.FOOD
            else -> CategoryIds.OTHER_EXPENSE
        }
    }

    private fun containsAny(text: String, vararg words: String): Boolean =
        words.any { text.contains(it, ignoreCase = true) }

    private fun buildNote(body: String, sender: String?): String {
        val clean = body.replace(Regex("\\s+"), " ").trim()
        val prefix = if (!sender.isNullOrBlank()) "[${sender.take(20)}] " else ""
        return (prefix + clean).take(100)
    }

    /**
     * 短信去重指纹：发送方 + 正文 + 短信时间戳。
     * 同一条短信被系统/运营商重复投递时，指纹一致，会被数据库唯一索引忽略。
     */
    fun hash(sender: String?, body: String, timestamp: Long): String {
        val raw = "${sender.orEmpty()}|$body|$timestamp"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
