package com.jianji.app.sms

import com.jianji.app.data.model.BillType
import com.jianji.app.data.model.CategoryIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯 JVM 单元测试，不依赖 Android 设备。
 * 运行：./gradlew testDebugUnitTest
 */
class SmsParserTest {

    private val ts = 1_729_382_400_000L

    @Test
    fun parsesBankExpenseWithBalance() {
        val sms = "【招商银行】您尾号1234的储蓄卡10月20日12:30消费人民币25.50元，余额1000.00元。"
        val parsed = SmsParser.parse(sms, "95555", ts)
        assertNotNull(parsed)
        assertEquals(2550L, parsed!!.amountCents)
        assertEquals(BillType.EXPENSE, parsed.type)
        assertTrue(parsed.note.contains("招商银行"))
    }

    @Test
    fun parsesSalaryIncome() {
        val sms = "【工商银行】工资代发：您尾号8888账户10月20日收入人民币5000.00元，余额8000.00元。"
        val parsed = SmsParser.parse(sms, "95588", ts)
        assertNotNull(parsed)
        assertEquals(500_000L, parsed!!.amountCents)
        assertEquals(BillType.INCOME, parsed.type)
        assertEquals(CategoryIds.SALARY, parsed.categoryId)
    }

    @Test
    fun parsesRefundAsIncome() {
        val sms = "支付宝：您的退款人民币30.00元已到账，请注意查收。"
        val parsed = SmsParser.parse(sms, "95188", ts)
        assertNotNull(parsed)
        assertEquals(3000L, parsed!!.amountCents)
        assertEquals(BillType.INCOME, parsed.type)
        assertEquals(CategoryIds.REFUND, parsed.categoryId)
    }

    @Test
    fun parsesAmountBeforeKeyword() {
        val sms = "支付宝：你有一笔25.00元的支出，已支付成功。"
        val parsed = SmsParser.parse(sms, "95188", ts)
        assertNotNull(parsed)
        assertEquals(2500L, parsed!!.amountCents)
        assertEquals(BillType.EXPENSE, parsed.type)
    }

    @Test
    fun ignoresVerificationCode() {
        val sms = "【银行】您的验证码是123456，5分钟内有效，请勿泄露。"
        assertNull(SmsParser.parse(sms, "95555", ts))
    }

    @Test
    fun ignoresMarketingMessage() {
        val sms = "【商城】双十一狂欢，全场满300减50，点击领取优惠券。"
        assertNull(SmsParser.parse(sms, "106900", ts))
    }

    @Test
    fun ignoresMessageWithoutAmount() {
        val sms = "【银行】您尾号1234的信用卡已激活，祝您用卡愉快。"
        assertNull(SmsParser.parse(sms, "95555", ts))
    }

    @Test
    fun parsesAlipayIncomeWithoutMisreadingPayment() {
        val sms = "支付宝到账人民币100.00元"
        val parsed = SmsParser.parse(sms, "95188", ts)
        assertNotNull(parsed)
        assertEquals(10_000L, parsed!!.amountCents)
        assertEquals(BillType.INCOME, parsed.type)
    }

    @Test
    fun keepsBankMessageContainingLoginHint() {
        val sms = "【建设银行】您尾号6666的储蓄卡消费人民币88.00元，详情请登录手机银行查询。"
        val parsed = SmsParser.parse(sms, "95533", ts)
        assertNotNull(parsed)
        assertEquals(8800L, parsed!!.amountCents)
        assertEquals(BillType.EXPENSE, parsed.type)
    }

    @Test
    fun tradeKeywordDoesNotForceExpense() {
        val sms = "【银行】交易提醒：您账户收入人民币200.00元。"
        val parsed = SmsParser.parse(sms, "95555", ts)
        assertNotNull(parsed)
        assertEquals(20_000L, parsed!!.amountCents)
        assertEquals(BillType.INCOME, parsed.type)
    }

    @Test
    fun hashIsStableForSameMessage() {
        val a = SmsParser.hash("95555", "消费25.50元", ts)
        val b = SmsParser.hash("95555", "消费25.50元", ts)
        val c = SmsParser.hash("95555", "消费25.50元", ts + 1)
        assertEquals(a, b)
        assertTrue(a != c)
    }
}
