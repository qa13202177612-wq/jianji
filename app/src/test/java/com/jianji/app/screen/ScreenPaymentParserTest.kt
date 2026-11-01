package com.jianji.app.screen

import com.jianji.app.data.model.BillType
import com.jianji.app.data.model.CategoryIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenPaymentParserTest {

    @Test
    fun parsesWechatRealLayout() {
        // 微信支付成功页：商户名在金额上方，没有「收款方」标签
        val texts = listOf(
            "20:23",
            "支付成功",
            "品宴江南",
            "¥ 1390.00",
            "摇一摇，有优惠"
        )
        val payment = ScreenPaymentParser.parse(texts)
        assertNotNull(payment)
        assertEquals(139_000L, payment!!.amountCents)
        assertEquals("品宴江南", payment.merchant)
        assertEquals(CategoryIds.FOOD, payment.categoryId)
        assertEquals(BillType.EXPENSE, payment.type)
    }

    @Test
    fun parsesAlipayRealLayoutWithDiscount() {
        // 支付宝支付成功页：顶部是实付金额，下方是订单原价、立减、营销卡片
        val texts = listOf(
            "21:45",
            "支付成功",
            "¥4007.09",
            "中国人民财产保险股份有限公司",
            "¥4007.23",
            "支付宝随机立减",
            "- ¥0.14",
            "交易方式",
            "花呗",
            "支付有礼",
            "今天支付再省0.25元",
            "生活缴费红包",
            "完成"
        )
        val payment = ScreenPaymentParser.parse(texts)
        assertNotNull(payment)
        assertEquals(400_709L, payment!!.amountCents)
        assertEquals("中国人民财产保险股份有限公司", payment.merchant)
        assertEquals(BillType.EXPENSE, payment.type)
    }

    @Test
    fun parsesAlipayPaymentSuccess() {
        val texts = listOf(
            "支付成功",
            "¥25.50",
            "收款方",
            "美团",
            "付款方式",
            "余额宝"
        )
        val payment = ScreenPaymentParser.parse(texts)
        assertNotNull(payment)
        assertEquals(2550L, payment!!.amountCents)
        assertEquals(BillType.EXPENSE, payment.type)
        assertEquals(CategoryIds.FOOD, payment.categoryId)
        assertEquals("美团", payment.merchant)
    }

    @Test
    fun parsesWechatMerchant() {
        val texts = listOf(
            "微信支付",
            "支付成功",
            "¥120.00",
            "商户全称",
            "某某便利店",
            "当前状态",
            "支付成功"
        )
        val payment = ScreenPaymentParser.parse(texts)
        assertNotNull(payment)
        assertEquals(12_000L, payment!!.amountCents)
        assertEquals("某某便利店", payment.merchant)
        assertEquals(CategoryIds.SHOPPING, payment.categoryId)
    }

    @Test
    fun parsesRefundAsIncome() {
        val texts = listOf("退款成功", "¥30.00", "退款至", "余额")
        val payment = ScreenPaymentParser.parse(texts)
        assertNotNull(payment)
        assertEquals(BillType.INCOME, payment!!.type)
        assertEquals(CategoryIds.REFUND, payment.categoryId)
    }

    @Test
    fun ignoresFailedPayment() {
        assertNull(ScreenPaymentParser.parse(listOf("支付失败", "¥25.50", "请重试")))
    }

    @Test
    fun ignoresBalancePage() {
        assertNull(ScreenPaymentParser.parse(listOf("账户余额", "¥1000.00")))
    }

    @Test
    fun ignoresSuccessPageWithoutAmount() {
        assertNull(ScreenPaymentParser.parse(listOf("支付成功", "完成")))
    }

    @Test
    fun dedupeKeyIsStableWithinWindowAndChangesAcrossWindows() {
        val payment = ScreenPayment(2550L, BillType.EXPENSE, CategoryIds.FOOD, "美团", "")
        val now = 1_729_382_400_000L
        val first = ScreenPaymentParser.dedupeKey("com.eg.android.AlipayGphone", payment, now)
        val second = ScreenPaymentParser.dedupeKey("com.eg.android.AlipayGphone", payment, now + 30_000L)
        val later = ScreenPaymentParser.dedupeKey("com.eg.android.AlipayGphone", payment, now + 4 * 60_000L)
        assertEquals(first, second)
        assertNotEquals(first, later)
    }

    // ---------------------------------------------------------- 支付状态精度

    @Test
    fun detectsWechatAndAlipayPackages() {
        assertEquals(
            PaymentApp.WECHAT,
            ScreenPaymentParser.analyze(listOf("支付成功"), "com.tencent.mm").app
        )
        assertEquals(
            PaymentApp.ALIPAY,
            ScreenPaymentParser.analyze(listOf("支付成功"), "com.eg.android.AlipayGphone").app
        )
    }

    @Test
    fun wechatPendingPageIsNotRecorded() {
        val texts = listOf(
            "微信支付",
            "待支付",
            "¥25.50",
            "订单号",
            "4200001234567890123",
            "支付时间",
            "2026-10-22 20:00"
        )
        val analysis = ScreenPaymentParser.analyze(texts, "com.tencent.mm")
        assertEquals(PaymentStatus.PENDING, analysis.status)
        assertNull(analysis.payment)
    }

    @Test
    fun wechatTransferWaitingForConfirmIsPending() {
        val texts = listOf("转账给 张三", "待对方确认收款", "¥100.00")
        val analysis = ScreenPaymentParser.analyze(texts, "com.tencent.mm")
        assertEquals(PaymentStatus.PENDING, analysis.status)
        assertNull(analysis.payment)
    }

    @Test
    fun failedPaymentWithBalanceHintIsNotRecorded() {
        val texts = listOf("支付失败", "余额不足", "请更换支付方式", "¥25.50")
        val analysis = ScreenPaymentParser.analyze(texts, "com.tencent.mm")
        assertEquals(PaymentStatus.FAILED, analysis.status)
        assertNull(analysis.payment)
    }

    @Test
    fun alipayClosedOrderIsFailed() {
        val texts = listOf("交易关闭", "¥10.00", "订单号", "202610220001")
        val analysis = ScreenPaymentParser.analyze(texts, "com.eg.android.AlipayGphone")
        assertEquals(PaymentStatus.FAILED, analysis.status)
        assertNull(analysis.payment)
    }

    @Test
    fun refundPendingIsNotRecorded() {
        val texts = listOf("退款处理中", "¥30.00", "预计1-3个工作日到账")
        val analysis = ScreenPaymentParser.analyze(texts, "com.eg.android.AlipayGphone")
        assertEquals(PaymentStatus.PENDING, analysis.status)
        assertNull(analysis.payment)
    }

    @Test
    fun chatSentenceIsNotTreatedAsPaymentStatus() {
        // 微信聊天里出现「支付成功」四个字，但没有交易详情字段
        val texts = listOf("张三", "我刚支付成功了", "¥100.00")
        val analysis = ScreenPaymentParser.analyze(texts, "com.tencent.mm")
        assertEquals(PaymentStatus.UNKNOWN, analysis.status)
        assertNull(analysis.payment)
    }

    @Test
    fun statusLabelAndSpacedOcrTextAreParsed() {
        val texts = listOf("当前状态：支 付 成 功", "¥ 12.00", "收款方", "便利店")
        val payment = ScreenPaymentParser.parse(texts, "com.eg.android.AlipayGphone")
        assertNotNull(payment)
        assertEquals(1200L, payment!!.amountCents)
        assertEquals("便利店", payment.merchant)
    }

    @Test
    fun receivedTransferIsIncome() {
        val texts = listOf("微信支付", "收款成功", "¥50.00", "已存入零钱")
        val payment = ScreenPaymentParser.parse(texts, "com.tencent.mm")
        assertNotNull(payment)
        assertEquals(BillType.INCOME, payment!!.type)
        assertEquals(5000L, payment.amountCents)
        assertEquals(CategoryIds.OTHER_INCOME, payment.categoryId)
    }

    @Test
    fun orderNumberIsNotTreatedAsAmount() {
        val texts = listOf("支付成功", "交易单号", "4200001234567890123", "¥12.34")
        val payment = ScreenPaymentParser.parse(texts, "com.tencent.mm")
        assertNotNull(payment)
        assertEquals(1234L, payment!!.amountCents)
    }

    @Test
    fun discountAndOriginalPriceAreNotChosen() {
        val texts = listOf(
            "支付成功",
            "订单金额 ¥200.00",
            "原价 ¥180.00",
            "实付 ¥100.00",
            "立减 ¥80.00"
        )
        val payment = ScreenPaymentParser.parse(texts)
        assertNotNull(payment)
        assertEquals(10_000L, payment!!.amountCents)
    }

    @Test
    fun platformNameIsNotUsedAsMerchant() {
        val texts = listOf("微信支付", "支付成功", "¥10.00")
        val payment = ScreenPaymentParser.parse(texts, "com.tencent.mm")
        assertNotNull(payment)
        assertEquals("屏幕识别", payment!!.merchant)
    }

    @Test
    fun amountFarFromStatusIsRejectedWithoutReceipt() {
        val texts = listOf("支付成功", "a", "b", "c", "d", "e", "f", "¥10.00")
        val analysis = ScreenPaymentParser.analyze(texts, "com.tencent.mm")
        assertEquals(PaymentStatus.SUCCESS, analysis.status)
        assertNull(analysis.payment)
    }

    // ------------------------------------------------- 真实截图整页回归

    @Test
    fun parsesWechatScreenshotFullPage() {
        // 微信支付成功页：绿色状态 + 商户名在金额上方 + 底部营销卡片
        val texts = listOf(
            "20:23", "54",
            "支付成功",
            "品宴江南",
            "¥1390.00",
            "摇一摇，有优惠"
        )
        val analysis = ScreenPaymentParser.analyze(texts, "com.tencent.mm")
        assertEquals(PaymentStatus.SUCCESS, analysis.status)
        assertEquals(PaymentApp.WECHAT, analysis.app)
        val payment = analysis.payment
        assertNotNull(payment)
        assertEquals(139_000L, payment!!.amountCents)
        assertEquals("品宴江南", payment.merchant)
        assertEquals(CategoryIds.FOOD, payment.categoryId)
    }

    @Test
    fun parsesAlipayScreenshotFullPage() {
        // 支付宝支付成功页：顶部实付 4007.09，下方原价 4007.23、随机立减 0.14、营销卡片
        val texts = listOf(
            "21:45", "5G", "2",
            "支付成功",
            "回首页",
            "¥4007.09",
            "中国人民财产保险股份有限公司",
            "¥ 4007.23",
            "支付宝随机立减",
            "- ¥ 0.14",
            "交易方式",
            "花呗",
            "好运卡+1",
            "充值金+15",
            "一键领",
            "支付有礼",
            "即将过期",
            "0.25元",
            "今天支付再省0.25元",
            "还有3个红包待使用",
            "去看看",
            "生活缴费红包",
            "生活缴费",
            "缴水电燃费立减",
            "0.5元",
            "免费领",
            "2000g 芭芭农场肥料",
            "芭芭农场",
            "限时翻倍",
            "话费周期券",
            "惠享充",
            "满50元可用",
            "1.5元",
            "免费领",
            "完成"
        )
        val analysis = ScreenPaymentParser.analyze(texts, "com.eg.android.AlipayGphone")
        assertEquals(PaymentStatus.SUCCESS, analysis.status)
        assertEquals(PaymentApp.ALIPAY, analysis.app)
        val payment = analysis.payment
        assertNotNull(payment)
        assertEquals(400_709L, payment!!.amountCents)
        assertEquals("中国人民财产保险股份有限公司", payment.merchant)
        assertEquals(BillType.EXPENSE, payment.type)
    }

    @Test
    fun parsesEnglishWechatMiniProgramSuccessPage() {
        // 微信小程序支付成功页可能是英文：Payment successful / 拼多多 / 0.96
        val texts = listOf(
            "2:32",
            "85%",
            "O Payment successful",
            "拼多多",
            "0.96",
            "Back to vendor"
        )
        val analysis = ScreenPaymentParser.analyze(texts, "com.tencent.mm")
        assertEquals(PaymentStatus.SUCCESS, analysis.status)
        assertEquals(PaymentApp.WECHAT, analysis.app)
        val payment = analysis.payment
        assertNotNull(payment)
        assertEquals(96L, payment!!.amountCents)
        assertEquals("拼多多", payment.merchant)
        assertEquals(BillType.EXPENSE, payment.type)
        assertEquals(CategoryIds.SHOPPING, payment.categoryId)
    }

    @Test
    fun parsesPinduoduoWechatPasswordFreeSuccessPage() {
        val texts = listOf(
            "2:35",
            "支付成功",
            "微信免密支付",
            "实付 ¥0.01",
            "拼多多",
            "查看订单"
        )
        val analysis = ScreenPaymentParser.analyze(texts, "com.xunmeng.pinduoduo")
        assertEquals(PaymentStatus.SUCCESS, analysis.status)
        val payment = analysis.payment
        assertNotNull(payment)
        assertEquals(1L, payment!!.amountCents)
        assertEquals("拼多多", payment.merchant)
        assertEquals(BillType.EXPENSE, payment.type)
        assertEquals(CategoryIds.SHOPPING, payment.categoryId)
    }

    @Test
    fun parsesPinduoduoPasswordFreeDeductedBanner() {
        // 真实免密页：扣款横幅与「支付成功」之间隔了很多商品/优惠行
        val texts = listOf(
            "2:38",
            "优惠",
            "Weixin Pay: Deducted: ¥1.24",
            "退货包运费",
            "预计后天送达",
            "医用棉签",
            "仅47分钟¥1.24",
            "平台优惠0.06元",
            "免费服务",
            "已选:1袋共50支",
            "支付成功",
            "10袋共500支",
            "15袋共750支"
        )
        val analysis = ScreenPaymentParser.analyze(texts, "com.xunmeng.pinduoduo")
        assertEquals(PaymentStatus.SUCCESS, analysis.status)
        val payment = analysis.payment
        assertNotNull(payment)
        assertEquals(124L, payment!!.amountCents)
        assertEquals("医用棉签", payment.merchant)
        assertEquals(BillType.EXPENSE, payment.type)
    }

    @Test
    fun parsesPinduoduoOrderPlacedWithDeductedBanner() {
        // 免密支付后直接回到「下单成功」页，页面上没有「支付成功」四个字
        val texts = listOf(
            "2:38",
            "Weixin Pay: Deducted: ¥1.24",
            "下单成功",
            "预计后天送达",
            "查看订单",
            "继续逛逛"
        )
        val analysis = ScreenPaymentParser.analyze(texts, "com.xunmeng.pinduoduo")
        assertEquals(PaymentStatus.SUCCESS, analysis.status)
        val payment = analysis.payment
        assertNotNull(payment)
        assertEquals(124L, payment!!.amountCents)
        assertEquals("拼多多", payment.merchant)
        assertEquals(BillType.EXPENSE, payment.type)
    }

    @Test
    fun parsesPinduoduoDeferredPaymentOrderPage() {
        // 先用后付：下单成功，实付 0 元，真正金额在商品金额里
        val texts = listOf(
            "2:42",
            "下单成功",
            "先用后付",
            "确认收货后付款",
            "商品金额 ¥1.24",
            "实付 ¥0.00",
            "拼多多"
        )
        val analysis = ScreenPaymentParser.analyze(texts, "com.xunmeng.pinduoduo")
        assertEquals(PaymentStatus.SUCCESS, analysis.status)
        val payment = analysis.payment
        assertNotNull(payment)
        assertEquals(124L, payment!!.amountCents)
        assertEquals("拼多多", payment.merchant)
        assertEquals(BillType.EXPENSE, payment.type)
        assertEquals(CategoryIds.SHOPPING, payment.categoryId)
        assertTrue(payment.deferred)
    }

    @Test
    fun ignoresRefundTextOnPinduoduoOrderPage() {
        // 订单列表里的历史退款文案不是新退款，不能自动记成收入
        val texts = listOf(
            "2.78元将退到微信钱包（预计20分钟内到账）",
            "退款成功",
            "拼多多"
        )
        val analysis = ScreenPaymentParser.analyze(texts, "com.xunmeng.pinduoduo")
        assertEquals(PaymentStatus.REFUND_SUCCESS, analysis.status)
        assertNull(analysis.payment)
    }

    // ------------------------------------------------- 重复入账防线（点开已支付订单）

    @Test
    fun billListPageWithMultipleAmountsIsNotRecorded() {
        // 微信「账单」列表页：多行「已支付 + 互不相同金额」全是历史记录，
        // 没有单号、没有交易详情字段时不能猜一条入账。
        val texts = listOf(
            "账单",
            "¥15.00", "美团外卖", "已支付",
            "¥9.90", "叮咚买菜", "已支付",
            "¥30.00", "滴滴出行", "已支付"
        )
        val analysis = ScreenPaymentParser.analyze(texts, "com.tencent.mm")
        assertNull(analysis.payment)
        assertEquals("multi-amount-list", analysis.reason)
    }

    @Test
    fun pastDatedTransactionDetailIsNotRecorded() {
        // 两天前的订单详情页（带支付时间与交易单号）：不是新交易。
        val twoDaysAgo = java.time.LocalDate.now().minusDays(2).toString()
        val texts = listOf(
            "账单详情", "已支付", "¥68.00",
            "支付时间", "$twoDaysAgo 19:32",
            "交易单号", "4200009876543210987"
        )
        val analysis = ScreenPaymentParser.analyze(texts, "com.tencent.mm")
        assertNull(analysis.payment)
        assertEquals("past-transaction-date", analysis.reason)
    }

    @Test
    fun sameDayDetailPageWithTxnNoHasStableKeyAcrossTime() {
        // 当天重开的详情页可以入账，但 key 由单号决定：
        // 无论隔多久再打开，dedupeKey 都一样，数据库唯一索引兜底去重。
        val today = java.time.LocalDate.now().toString()
        val texts = listOf(
            "账单详情", "已支付", "¥68.00",
            "支付时间", "$today 19:32",
            "交易单号", "42000011112222333"
        )
        val payment = ScreenPaymentParser.parse(texts, "com.tencent.mm")
        assertNotNull(payment)
        assertEquals(6800L, payment!!.amountCents)
        assertEquals("42000011112222333", payment.txnNo)
        val now = 1_729_382_400_000L
        val first = ScreenPaymentParser.dedupeKey("com.tencent.mm", payment, now)
        val daysLater = ScreenPaymentParser.dedupeKey("com.tencent.mm", payment, now + 7 * 24 * 3600_000L)
        assertEquals(first, daysLater)
    }
}
