package com.jianji.app.screen

import com.jianji.app.data.model.BillType
import com.jianji.app.data.model.CategoryIds
import com.jianji.app.util.MoneyUtils
import java.security.MessageDigest
import java.time.LocalDate
import kotlin.math.abs

/** 识别到的支付 App，用于区分微信/支付宝的页面文案差异。 */
enum class PaymentApp {
    WECHAT,
    ALIPAY,
    UNIONPAY,
    UNKNOWN;

    companion object {
        fun fromPackage(packageName: String?): PaymentApp = when (packageName) {
            "com.tencent.mm" -> WECHAT
            "com.eg.android.AlipayGphone" -> ALIPAY
            "com.unionpay" -> UNIONPAY
            else -> UNKNOWN
        }
    }
}

/**
 * 支付页状态。
 *
 * 只有 [SUCCESS] / [REFUND_SUCCESS] 会生成账单；
 * [PENDING] / [FAILED] 仅用于日志和「是否继续 OCR」的判断。
 */
enum class PaymentStatus {
    SUCCESS,
    REFUND_SUCCESS,
    PENDING,
    FAILED,
    UNKNOWN
}

data class ScreenPayment(
    val amountCents: Long,
    val type: BillType,
    val categoryId: String,
    val merchant: String,
    val rawText: String,
    val app: PaymentApp = PaymentApp.UNKNOWN,
    val status: PaymentStatus = PaymentStatus.SUCCESS,
    /** 拼多多等平台的「先用后付」订单：现在下单，确认收货后再实际扣款。 */
    val deferred: Boolean = false,
    /** 交易单号/商户单号等凭证号。有它时同一笔交易的重开页面可以稳定去重。 */
    val txnNo: String? = null
)

/**
 * 一次屏幕分析的完整结果。
 *
 * [payment] 非空且 [isRecordable] 为 true 时才应该写入数据库。
 * 失败/待支付页也会返回明确的 [status]，方便服务层跳过 OCR、打印原因。
 */
data class ScreenAnalysis(
    val app: PaymentApp,
    val status: PaymentStatus,
    val payment: ScreenPayment?,
    val reason: String = "",
    val hasPaymentEvidence: Boolean = false
) {
    val isRecordable: Boolean
        get() = payment != null &&
            (status == PaymentStatus.SUCCESS || status == PaymentStatus.REFUND_SUCCESS)
}

/**
 * 支付成功页文本解析器（纯 JVM，可单元测试）。
 *
 * 输入可以来自：
 * - AccessibilityService 读取的文本节点；
 * - AccessibilityService.takeScreenshot() + ML Kit OCR 的识别结果。
 *
 * 识别顺序：
 * 1. 先判断支付状态（失败 > 待支付 > 退款成功 > 成功），失败/待支付直接不记账；
 * 2. 只有「明确的成功状态 + 金额」才生成账单；
 * 3. 对微信/支付宝的典型页面结构分别处理金额、商户、收支方向。
 *
 * 相比只搜索「支付成功」四个字，这里额外做了：
 * - 状态必须是独立的状态行（允许「当前状态：支付成功」这类标签）；
 * - 聊天里「我刚支付成功了」这种长句默认不算状态；
 * - 状态与金额相距过远、且页面没有交易凭证信息时，判定为误配；
 * - 金额排除订单号、时间、余额、优惠、立减、原价等干扰项。
 */
object ScreenPaymentParser {

    private const val FALLBACK_MERCHANT = "屏幕识别"

    /** 免密支付页往往不显示商户名，用商户 App 名兜底。 */
    private val PACKAGE_MERCHANTS = mapOf(
        "com.xunmeng.pinduoduo" to "拼多多",
        "com.taobao.taobao" to "淘宝",
        "com.jingdong.app.mall" to "京东",
        "com.sankuai.meituan" to "美团",
        "me.ele" to "饿了么"
    )

    /** 先用后付/先享后付页面的特征文案。 */
    private val DEFERRED_HINTS = listOf(
        "先用后付", "先享后付", "0元下单", "确认收货后付款",
        "收货后付款", "先试用后付款", "先用后付订单"
    )

    /** 先用后付订单页没有「支付成功」，用下单成功类文案作为成功信号。 */
    private val ORDER_SUCCESS_WORDS = listOf(
        "下单成功", "订单提交成功", "订单已提交", "订单创建成功"
    )

    // ---------------------------------------------------------------- 状态词

    private data class StatusRule(
        val status: PaymentStatus,
        val phrases: List<String>
    )

    private enum class StatusMatch(val priority: Int) {
        EMBEDDED(1),
        NEAR(2),
        LABEL(3),
        EXACT(4)
    }

    private data class StatusHit(
        val status: PaymentStatus,
        val phrase: String,
        val lineIndex: Int,
        val match: StatusMatch
    )

    private val STATUS_RULES = listOf(
        // 失败/关闭优先级最高：页面上可能同时残留「支付成功」的历史文案
        StatusRule(
            PaymentStatus.FAILED,
            listOf(
                "支付失败", "付款失败", "交易失败", "转账失败", "扣款失败", "退款失败",
                "支付未成功", "付款未成功", "交易未成功",
                "支付超时", "付款超时", "交易超时",
                "交易已关闭", "交易关闭", "订单已关闭", "订单关闭",
                "订单已取消", "支付已取消", "交易已取消", "付款取消", "支付取消",
                "支付未完成", "付款未完成", "未完成支付", "未完成付款",
                "银行卡余额不足", "余额不足",
                "已取消", "已关闭",
                // 英文页面（微信小程序、国际版等）
                "paymentfailed", "paymentfailure", "transactionfailed", "transferfailed",
                "paymentdeclined", "paymentcanceled", "paymentcancelled",
                "paymenttimeout", "insufficientbalance",
                "orderclosed", "ordercanceled", "ordercancelled", "closed"
            )
        ),
        // 待支付/处理中：不是成功，也不能截图后当成成功
        StatusRule(
            PaymentStatus.PENDING,
            listOf(
                "订单待支付", "订单待付款", "订单未支付", "待对方确认收款",
                "等待对方确认收款", "待对方确认", "等待对方确认",
                "等待付款", "等待支付", "等待你付款", "请尽快付款", "请及时付款",
                "交易处理中", "订单处理中", "支付处理中",
                "退款处理中", "退款已受理", "退款申请中",
                "支付中", "付款中", "退款中",
                "待支付", "待付款",
                "尚未支付", "未支付",
                "处理中", "确认中",
                "pendingpayment", "awaitingpayment", "waitingforpayment", "tobepaid",
                "paymentprocessing", "processingpayment", "transactionprocessing",
                "refundprocessing", "processing", "pending",
                "willbededucted", "tobededucted", "willbecharged"
            )
        ),
        // 退款成功属于收入，必须排在「支付成功」之前
        StatusRule(
            PaymentStatus.REFUND_SUCCESS,
            listOf(
                "退款成功", "退款到账", "退款已到账", "已原路退回", "原路退回",
                "退款完成", "已退款",
                "refundsuccessful", "refundsuccess", "refundcompleted",
                "refunded", "refundtooriginal"
            )
        ),
        StatusRule(
            PaymentStatus.SUCCESS,
            listOf(
                "支付成功", "付款成功", "交易成功", "转账成功", "扣款成功",
                "支付完成", "付款完成",
                "收款成功", "到账成功", "收款到账", "已到账", "已收款",
                "已存入零钱", "存入零钱",
                "已支付", "已付款",
                "paymentsuccessful", "paymentsuccess", "paymentcompleted", "paymentcomplete",
                "transactionsuccessful", "transactionsuccess", "transactioncompleted",
                "transfersuccessful", "transfersuccess", "paidsuccessfully", "paid",
                "receivedsuccessfully", "collectionsuccessful", "topupsuccessful",
                // 免密支付
                "免密支付成功", "已扣款", "扣款完成", "订单已支付",
                "deducted"
            )
        )
    )

    /**
     * 含义比较宽泛、容易出现在聊天/列表里的状态词。
     * 这类词只接受「整行就是这个状态」或「状态：xxx」，避免把聊天句子当成支付状态。
     */
    private val WEAK_PHRASES = setOf(
        "已取消", "已关闭", "支付取消", "付款取消",
        "未支付", "尚未支付", "处理中", "确认中", "支付中",
        "余额不足", "银行卡余额不足",
        "已支付", "已付款", "已到账", "已收款", "到账成功",
        "已退款", "退款完成",
        "closed", "processing", "pending", "paid", "received", "refunded",
        "deducted", "已扣款"
    )

    private val ALL_STATUS_PHRASES: List<String> = STATUS_RULES.flatMap { it.phrases }

    private val STATUS_LABELS = listOf(
        "当前状态", "交易状态", "支付状态", "付款状态", "订单状态",
        "支付结果", "交易结果", "状态", "结果",
        "paymentstatus", "transactionstatus", "status", "result"
    )

    // ------------------------------------------------------------ 页面证据

    /** 出现在真实交易详情里的字段。用来判断一段文字是不是「交易页面」。 */
    private val RECEIPT_EVIDENCE = listOf(
        "交易单号", "商户单号", "商家订单号", "订单编号", "订单号", "交易号", "流水号",
        "账单详情", "支付时间", "付款时间", "交易时间", "转账时间", "创建时间",
        "支付方式", "付款方式", "交易方式", "收款方式",
        "收款方", "收款人", "商户全称", "商户名称", "付款方", "转账给", "付款给", "对方户名",
        // 免密支付/扣款凭证
        "deducted", "已扣款", "免密支付",
        // 先用后付
        "先用后付", "先享后付", "确认收货后付款"
    )

    // ------------------------------------------------------- 凭证号 / 交易时间

    /** 单号类标签：拿到单号后，同一笔交易的重开页面能生成同一个去重 key。 */
    private val TXN_LABELS = listOf(
        "交易单号", "商户单号", "商家订单号", "订单编号", "订单号", "交易号",
        "流水号", "账单编号", "支付单号", "退款单号", "订单id", "交易id",
        "transactionid", "tradeno", "orderno", "orderid", "serialnumber",
        "paymentno", "referenceno"
    )

    private val TXN_VALUE = Regex("""[A-Za-z0-9][A-Za-z0-9_\-]{7,31}""")

    /** 明确带日期的交易时间标签：用于识别「历史订单详情页」。 */
    private val TRANSACTION_TIME_LABELS = listOf(
        "支付时间", "付款时间", "交易时间", "创建时间", "下单时间", "扣款时间",
        "paymenttime", "transactiontime", "timepaid"
    )

    private val ISO_DATE = Regex("""(20\d{2})[-/.年](\d{1,2})[-/.月](\d{1,2})""")

    /**
     * 提取交易单号/订单号（LLM 兜底共用）。
     * 值形如微信 42000xxxxxx、支付宝 2026xxxxxx、商户单号字母数字混合，
     * 至少 5 位数字才算真单号；纯日期（如 2026-10-21）不能当单号。
     */
    fun extractTxnNo(lines: List<String>): String? {
        for (index in lines.indices) {
            val lower = lines[index].lowercase()
            val label = TXN_LABELS.firstOrNull { lower.contains(it) } ?: continue
            val sameLine = lower.substringAfter(label)
            val nextLine = lines.getOrNull(index + 1).orEmpty().lowercase()
            for (segment in listOf(sameLine, nextLine)) {
                val match = TXN_VALUE.find(segment) ?: continue
                val value = match.value
                if (ISO_DATE.matchEntire(value) != null) continue
                if (value.count { it.isDigit() } < 5) continue
                return value.uppercase()
            }
        }
        return null
    }

    /**
     * 页面「支付时间/交易时间/创建时间」早于今天 => 这是历史订单详情，
     * 不是刚发生的一笔交易：用户点开旧订单查看时绝不能重复记账。
     * （LLM 兜底共用；跨天的正常补录仍可由短信账单/手动记账完成。）
     */
    fun hasPastTransactionDate(lines: List<String>, today: LocalDate = LocalDate.now()): Boolean {
        for (index in lines.indices) {
            val lower = lines[index].lowercase()
            if (TRANSACTION_TIME_LABELS.none { lower.contains(it) }) continue
            val segments = listOf(lines[index], lines.getOrNull(index + 1).orEmpty())
            for (segment in segments) {
                val match = ISO_DATE.find(segment) ?: continue
                val date = runCatching {
                    LocalDate.of(
                        match.groupValues[1].toInt(),
                        match.groupValues[2].toInt(),
                        match.groupValues[3].toInt()
                    )
                }.getOrNull() ?: continue
                if (date.isBefore(today)) return true
            }
        }
        return false
    }

    // ---------------------------------------------------------------- 金额

    private val CURRENCY_HINTS = listOf("¥", "￥", "RMB", "CNY")

    /** 「实付」优先于「订单金额」，避免把原价/应付金额记成实际扣款。 */
    private val PAID_AMOUNT_HINTS = listOf(
        "实付", "实际支付", "支付金额", "付款金额", "扣款金额",
        "amountpaid", "paymentamount", "paid", "deducted", "已扣款"
    )
    private val AMOUNT_STRONG_HINTS = listOf(
        "交易金额", "收款金额", "退款金额", "到账金额", "订单金额", "商品金额",
        "amount", "total"
    )
    private val AMOUNT_WEAK_HINTS = listOf(
        "金额", "合计", "总计", "共计", "应付",
        "subtotal", "due"
    )

    private val EXCLUDE_AMOUNT_LINES = listOf(
        "余额", "优惠", "红包", "积分", "手续费", "限额", "可用", "折扣", "立减",
        "随机", "原价", "省", "券", "单号", "订单号", "交易号", "流水号", "编号",
        "尾号", "卡号", "账号", "手机号", "数量", "件数", "日期", "时间",
        "balance", "discount", "coupon", "original", "save", "orderno",
        "ordernumber", "orderid", "transactionid", "paymenttime", "transactiontime",
        "cardnumber", "account", "quantity", "items"
    )

    private val AMOUNT_REGEX = Regex("""([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
    private val DATE_TIME_TOKEN = Regex(
        """\d{1,2}:\d{2}(?::\d{2})?|\d{4}[-/.年]\d{1,2}[-/.月]\d{1,2}日?"""
    )
    private val DATE_TIME_LINE = Regex(
        """\d{1,2}:\d{2}|\d{4}[-/.年]\d{1,2}[-/.月]\d{1,2}"""
    )
    /** 10 位以上连续数字不可能是金额（本项目金额上限 99,999,999.99）。 */
    private val LONG_DIGITS = Regex("""\d{10,}""")
    /** 整行只有一个金额（支付成功页的大号字体金额就是这种独立一行）。 */
    private val STANDALONE_AMOUNT =
        Regex("""[¥￥]?\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?[¥￥]?""")

    private data class AmountSelection(
        val cents: Long,
        val lineIndex: Int
    )

    private data class AmountCandidate(
        val cents: Long,
        val lineIndex: Int,
        val score: Int
    )

    // ---------------------------------------------------------------- 商户

    private val MERCHANT_LABELS = listOf(
        "商户全称", "商户名称", "商家名称", "收款商户",
        "收款方", "收款人", "付款给", "转账给", "对方户名", "商户"
    )

    private val PAYMENT_METHOD_WORDS = listOf(
        "余额宝", "花呗", "银行卡", "零钱", "信用卡", "储蓄卡",
        "付款方式", "支付方式", "交易方式",
        "免密支付", "微信免密支付", "免密",
        "先用后付", "先享后付", "0元下单", "确认收货后付款", "收货后付款"
    )

    private val MERCHANT_REJECT_WORDS = listOf(
        "支付成功", "付款成功", "交易成功", "当前状态", "交易状态", "支付状态",
        "订单状态", "交易单号", "商户单号", "订单号", "支付时间", "付款时间",
        "微信支付", "支付宝", "云闪付",
        // 退款提示/到账通知不能被当成商户名
        "退到", "到账", "预计", "分钟内", "钱包", "将退", "元将退", "退款"
    )

    private val NOISE_WORDS = listOf(
        "回首页", "摇一摇", "支付有礼", "红包", "优惠", "立减", "随机", "好运卡",
        "充值金", "免费领", "去看看", "即将过期", "原价", "余额", "手续费",
        "限时", "积分", "省", "券", "满", "首页", "详情", "返回", "我知道了",
        "复制", "投诉", "联系商家", "微信支付", "支付宝", "云闪付", "银联",
        "退款至", "退款到", "退款方式",
        "免密支付", "微信免密支付", "开通免密", "免密支付管理",
        "退货包运费", "退货", "运费", "送达", "包邮", "免费服务",
        "已选", "查看订单", "继续逛逛", "下单成功", "拼单", "收藏",
        "先用后付", "先享后付", "0元下单", "确认收货后付款", "收货后付款",
        "待收货", "待发货", "订单详情", "确认收货"
    )

    private val COMPANY_SUFFIX = Regex(
        """(?:股份有限公司|有限责任公司|有限公司|集团|公司|餐厅|饭店|酒店|超市|商场|药房|医院|银行|门店)$"""
    )

    private val COMPACT_STRIP = Regex("""[\s 　，。！!、~～:：;；\-—–_·✓✔√•]+""")
    private val QUESTION_MARKERS = setOf('吗', '么', '呢', '吧', '没', '不', '未', '否', '无', '?', '？')
    private val HEAD_REJECT_MARKERS = setOf('我', '你', '他', '她', '它', '咱', '刚')

    // ---------------------------------------------------------------- 入口

    fun parse(texts: List<String>, packageName: String? = null): ScreenPayment? =
        analyze(texts, packageName).payment

    /** 拼多多/淘宝等商户 App：这些 App 内的退款历史不自动记账。 */
    fun isMerchantApp(packageName: String?): Boolean =
        packageName != null && PACKAGE_MERCHANTS.containsKey(packageName)

    /** 商户 App 没有显示商户名时的兜底名称。 */
    fun packageMerchant(packageName: String?): String? =
        packageName?.let { PACKAGE_MERCHANTS[it] }

    /** 供千问兜底复用同一套分类规则。 */
    fun categoryFor(text: String, type: BillType): String = guessCategory(text, type)

    fun analyze(texts: List<String>, packageName: String? = null): ScreenAnalysis {
        return try {
            val lines = normalize(texts)
            val app = detectApp(packageName, lines)
            if (lines.isEmpty()) {
                return ScreenAnalysis(app, PaymentStatus.UNKNOWN, null, "empty")
            }

            val hasReceipt = hasReceiptEvidence(lines)
            val deferred = isDeferredPage(lines)
            val hit = findStatus(lines, hasReceipt)
                ?: (if (deferred) findDeferredSuccess(lines) else null)
                ?: return ScreenAnalysis(
                    app, PaymentStatus.UNKNOWN, null, "no-status", hasReceipt
                )

            if (hit.status == PaymentStatus.FAILED || hit.status == PaymentStatus.PENDING) {
                return ScreenAnalysis(
                    app,
                    hit.status,
                    null,
                    "status=${hit.status.name} phrase=${hit.phrase}",
                    hasReceipt
                )
            }

            // 拼多多/淘宝等商户 App 的订单列表里经常残留历史「退款成功」文案，
            // 那不是新退款，自动记成收入会造成大量误记。退款只从微信/支付宝等
            // 支付账单页识别，商户 App 内退款由用户手动记录。
            if (hit.status == PaymentStatus.REFUND_SUCCESS &&
                packageName != null &&
                PACKAGE_MERCHANTS.containsKey(packageName)
            ) {
                return ScreenAnalysis(
                    app, hit.status, null, "refund-on-merchant-page", hasReceipt
                )
            }

            // 防线1：历史订单详情（支付/交易时间早于今天）不是新交易，绝不入账。
            if (hasPastTransactionDate(lines)) {
                return ScreenAnalysis(
                    app, hit.status, null, "past-transaction-date", hasReceipt
                )
            }

            val txnNo = extractTxnNo(lines)

            // 防线2：账单/订单列表页——只有弱状态词（已支付）、页面上有多个
            // 互不相同的金额、又拿不到单号时，无法确定要记哪一笔，直接放弃，
            // 避免把旧订单再记一遍或张冠李戴。
            val phrase = hit.phrase.substringAfter(':')
            if (!hasReceipt && txnNo == null && phrase in WEAK_PHRASES &&
                collectAmountCandidates(lines).map { it.cents }.distinct().size >= 2
            ) {
                return ScreenAnalysis(app, hit.status, null, "multi-amount-list", false)
            }

            val amount = extractAmount(lines, hit.lineIndex)
                ?: return ScreenAnalysis(
                    app, hit.status, null, "no-amount", hasReceipt
                )

            // 状态和金额理应出现在同一块支付卡片里。相隔太远又没有交易凭证时，
            // 多半是聊天/通知列表里同时出现了「支付成功」和某个金额。
            if (!hasReceipt && abs(hit.lineIndex - amount.lineIndex) > 6) {
                return ScreenAnalysis(
                    app, hit.status, null, "amount-too-far", false
                )
            }

            val joined = lines.joinToString("\n")
            val type = detectType(joined, hit.status)
            val extractedMerchant = extractMerchant(lines, amount.lineIndex)
            val packageMerchant = packageName?.let { PACKAGE_MERCHANTS[it] }
            val merchant = if (extractedMerchant == FALLBACK_MERCHANT) {
                packageMerchant ?: FALLBACK_MERCHANT
            } else {
                extractedMerchant
            }
            val categoryId = guessCategory(joined, type)

            val payment = ScreenPayment(
                amountCents = amount.cents,
                type = type,
                categoryId = categoryId,
                merchant = merchant,
                rawText = joined.take(300),
                app = app,
                status = hit.status,
                deferred = deferred,
                txnNo = txnNo
            )
            ScreenAnalysis(app, hit.status, payment, "ok phrase=${hit.phrase}", hasReceipt)
        } catch (t: Throwable) {
            ScreenAnalysis(
                PaymentApp.UNKNOWN,
                PaymentStatus.UNKNOWN,
                null,
                "error:${t.javaClass.simpleName}"
            )
        }
    }

    private fun normalize(texts: List<String>): List<String> =
        texts.asSequence()
            .map {
                it.replace(' ', ' ')
                    .replace(' ', ' ')
                    .replace('　', ' ')
                    .trim()
            }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(300)
            .toList()

    private fun detectApp(packageName: String?, lines: List<String>): PaymentApp {
        val fromPackage = PaymentApp.fromPackage(packageName)
        if (fromPackage != PaymentApp.UNKNOWN) return fromPackage

        val text = lines.joinToString(" ")
        return when {
            text.contains("微信支付") || text.contains("零钱") || text.contains("微信") ->
                PaymentApp.WECHAT
            text.contains("支付宝") || text.contains("余额宝") || text.contains("花呗") ->
                PaymentApp.ALIPAY
            text.contains("云闪付") || text.contains("银联") ->
                PaymentApp.UNIONPAY
            else -> PaymentApp.UNKNOWN
        }
    }

    // ------------------------------------------------------------ 状态识别

    private fun findStatus(lines: List<String>, hasReceipt: Boolean): StatusHit? {
        for (rule in STATUS_RULES) {
            var best: StatusHit? = null
            for (phrase in rule.phrases) {
                lines.forEachIndexed { index, line ->
                    val match = matchStatusLine(line, phrase) ?: return@forEachIndexed
                    if (!acceptMatch(match, phrase, hasReceipt)) return@forEachIndexed

                    val current = best
                    if (current == null || match.priority > current.match.priority) {
                        best = StatusHit(rule.status, phrase, index, match)
                    }
                }
            }
            if (best != null) return best
        }
        return null
    }

    private fun matchStatusLine(line: String, phrase: String): StatusMatch? {
        val compact = compactLine(line)
        if (compact.isEmpty()) return null

        if (compact == phrase) return StatusMatch.EXACT

        val labeled = stripStatusLabel(compact)
        if (labeled != null) {
            if (labeled == phrase) return StatusMatch.LABEL
            if (labeled.startsWith(phrase)) {
                val tail = labeled.substring(phrase.length)
                if (tail.length <= 2 && isPlainTail(tail)) return StatusMatch.LABEL
            }
            if (labeled.endsWith(phrase)) {
                val head = labeled.substring(0, labeled.length - phrase.length)
                if (head.length <= 2 && isPlainHead(head)) return StatusMatch.LABEL
            }
        }

        if (compact.startsWith(phrase)) {
            val tail = compact.substring(phrase.length)
            if (tail.length <= 4 && isPlainTail(tail)) return StatusMatch.NEAR
        }
        if (compact.endsWith(phrase)) {
            val head = compact.substring(0, compact.length - phrase.length)
            if (head.length <= 4 && isPlainHead(head)) return StatusMatch.NEAR
        }

        return if (compact.contains(phrase)) StatusMatch.EMBEDDED else null
    }

    private fun acceptMatch(
        match: StatusMatch,
        phrase: String,
        hasReceipt: Boolean
    ): Boolean = when (match) {
        StatusMatch.EXACT, StatusMatch.LABEL -> true
        // 宽泛状态词出现在长句里时，必须有交易详情字段兜底
        StatusMatch.NEAR -> phrase !in WEAK_PHRASES || hasReceipt
        StatusMatch.EMBEDDED -> hasReceipt
    }

    private fun compactLine(line: String): String =
        line.replace(COMPACT_STRIP, "").lowercase()

    private fun stripStatusLabel(compact: String): String? {
        for (label in STATUS_LABELS) {
            if (compact.startsWith(label) && compact.length > label.length) {
                return compact.substring(label.length)
            }
        }
        return null
    }

    private fun isPlainTail(tail: String): Boolean =
        tail.none { it in QUESTION_MARKERS }

    /** 排除「我支付成功」「刚刚支付成功」这类聊天口吻。 */
    private fun isPlainHead(head: String): Boolean =
        head.none { it in QUESTION_MARKERS || it in HEAD_REJECT_MARKERS }

    private fun hasReceiptEvidence(lines: List<String>): Boolean =
        lines.any { line ->
            val lower = line.lowercase()
            RECEIPT_EVIDENCE.any { lower.contains(it) }
        }

    private fun isDeferredPage(lines: List<String>): Boolean =
        lines.any { line -> DEFERRED_HINTS.any { line.contains(it) } }

    /** 先用后付页面没有「支付成功」，用「下单成功」等文案确认订单已创建。 */
    private fun findDeferredSuccess(lines: List<String>): StatusHit? {
        for (phrase in ORDER_SUCCESS_WORDS) {
            lines.forEachIndexed { index, line ->
                val match = matchStatusLine(line, phrase) ?: return@forEachIndexed
                if (match == StatusMatch.EMBEDDED) return@forEachIndexed
                return StatusHit(
                    PaymentStatus.SUCCESS,
                    "先用后付:$phrase",
                    index,
                    match
                )
            }
        }
        return null
    }

    // ------------------------------------------------------------ 金额识别

    private fun collectAmountCandidates(lines: List<String>): List<AmountCandidate> {
        val candidates = ArrayList<AmountCandidate>()

        lines.forEachIndexed { index, raw ->
            if (isAmountExcludedLine(raw)) return@forEachIndexed
            val line = sanitizeAmountLine(raw)
            val score = amountLineScore(line)
            if (score <= 0) return@forEachIndexed

            for (match in AMOUNT_REGEX.findAll(line)) {
                val value = match.groupValues[1].trimEnd(',')
                if (!isPlausibleAmount(value, line, match.range.first)) continue
                val cents = MoneyUtils.parseToCents(value) ?: continue
                if (cents > 0L) {
                    candidates.add(AmountCandidate(cents, index, score))
                }
            }
        }
        return candidates
    }

    private fun extractAmount(lines: List<String>, statusLineIndex: Int): AmountSelection? {
        val candidates = collectAmountCandidates(lines)

        if (candidates.isNotEmpty()) {
            val best = candidates.sortedWith(
                compareByDescending<AmountCandidate> {
                    it.score + proximityBonus(it.lineIndex, statusLineIndex)
                }
                    .thenBy { abs(it.lineIndex - statusLineIndex) }
                    .thenBy { it.lineIndex }
            ).first()
            return AmountSelection(best.cents, best.lineIndex)
        }

        return extractFallbackAmount(lines)
    }

    /**
     * 「支付成功」和金额理应在同一张卡片上：离状态行越近，越可能是本次交易的
     * 实付金额。列表页/营销位里的其它金额离状态行远，会被拉开差距。
     */
    private fun proximityBonus(lineIndex: Int, statusLineIndex: Int): Int {
        return when (abs(lineIndex - statusLineIndex)) {
            in 0..1 -> 25
            in 2..3 -> 12
            in 4..6 -> 4
            else -> 0
        }
    }

    /** 兜底：整页只有一个带两位小数的金额时，才认为它是支付金额。 */
    private fun extractFallbackAmount(lines: List<String>): AmountSelection? {
        val found = ArrayList<Pair<Int, String>>()

        lines.forEachIndexed { index, raw ->
            if (isAmountExcludedLine(raw)) return@forEachIndexed
            val line = sanitizeAmountLine(raw)
            if (line.isEmpty()) return@forEachIndexed

            for (match in AMOUNT_REGEX.findAll(line)) {
                val value = match.groupValues[1].trimEnd(',')
                if (!value.contains('.')) continue
                if (!isPlausibleAmount(value, line, match.range.first)) continue
                found.add(index to value)
            }
        }

        if (found.map { it.second }.distinct().size != 1) return null
        val (index, value) = found.first()
        val cents = MoneyUtils.parseToCents(value) ?: return null
        return if (cents > 0L) AmountSelection(cents, index) else null
    }

    private fun amountLineScore(line: String): Int {
        val lower = line.lowercase()
        var score = 0
        if (CURRENCY_HINTS.any { lower.contains(it.lowercase()) }) score += 40
        when {
            PAID_AMOUNT_HINTS.any { lower.contains(it) } -> score += 50
            AMOUNT_STRONG_HINTS.any { lower.contains(it) } -> score += 35
            AMOUNT_WEAK_HINTS.any { lower.contains(it) } -> score += 15
        }
        // 只有已经命中金额关键词/货币符号的短行才加分，
        // 避免把「85%」电量、时间等短数字行当成金额。
        if (score > 0 && lower.length <= 20) score += 5
        // 整行就是一个数字/金额：支付成功页的大号金额正是这种独立行。
        if (score > 0 && STANDALONE_AMOUNT.matches(line.trim())) score += 8
        return score
    }

    private fun isAmountExcludedLine(line: String): Boolean {
        val text = line.trim()
        if (text.isEmpty()) return true
        val lower = text.lowercase()
        if (EXCLUDE_AMOUNT_LINES.any { lower.contains(it) }) return true
        if (text.startsWith('-') || text.startsWith('−') || text.startsWith('+')) return true
        if (text.contains("-¥") || text.contains("-￥") ||
            text.contains("−¥") || text.contains("−￥") ||
            text.contains("+¥") || text.contains("+￥")
        ) {
            return true
        }
        // 纯时间/日期行（如 "20:23"、"2026-10-22"）不参与金额兜底
        if (DATE_TIME_LINE.containsMatchIn(text) &&
            text.none { it == '¥' || it == '￥' }
        ) {
            return true
        }
        return false
    }

    private fun sanitizeAmountLine(line: String): String =
        line.replace(DATE_TIME_TOKEN, " ")
            .replace(LONG_DIGITS, " ")

    private fun isPlausibleAmount(value: String, line: String, start: Int): Boolean {
        val integerPart = value.substringBefore('.').replace(",", "")
        if (integerPart.isEmpty() || integerPart.length > 9) return false

        val before = line.substring(0, start).trimEnd()
        if (before.endsWith("-") || before.endsWith("−") || before.endsWith("+")) return false

        if (start > 0) {
            val prev = line[start - 1]
            if (prev in '0'..'9' || prev in 'a'..'z' || prev in 'A'..'Z' || prev == '.') {
                return false
            }
        }
        val end = start + value.length
        if (end < line.length) {
            val next = line[end]
            if (next in '0'..'9' || next == '.') return false
        }
        return true
    }

    // ------------------------------------------------------------ 商户识别

    private fun extractMerchant(lines: List<String>, amountLineIndex: Int): String {
        // 1. 明确标签：「收款方 美团」或「收款方」下一行「美团」
        for (index in lines.indices) {
            val line = lines[index]
            for (label in MERCHANT_LABELS) {
                val labelIndex = line.indexOf(label)
                if (labelIndex < 0) continue

                val value = line.substring(labelIndex + label.length)
                    .trim()
                    .trim(':', '：', '-', '—', ' ', '　')
                    .removeSuffix(">")
                    .trim()
                if (isMerchantValue(value)) return value

                val next = lines.getOrNull(index + 1)?.trim().orEmpty()
                if (value.isEmpty() && isMerchantValue(next)) {
                    return next.trim(':', '：', '-', '—', ' ', '　')
                }
            }
        }

        // 2. 企业/门店名：支付宝常见「中国人民财产保险股份有限公司」
        lines.firstOrNull { line ->
            line.length in 4..50 &&
                !line.contains('¥') &&
                !line.contains('￥') &&
                COMPANY_SUFFIX.containsMatchIn(line) &&
                !isNoise(line)
        }?.let { return it.trim() }

        // 3. 金额上一行：微信常见「品宴江南」在金额正上方
        for (index in (amountLineIndex - 1) downTo 0) {
            val candidate = lines[index].trim()
            if (candidate.isEmpty()) continue
            if (candidate.contains('¥') || candidate.contains('￥')) continue
            if (isAmountLike(candidate)) continue
            if (!isMerchantValue(candidate)) continue
            return candidate
        }

        // 4. 页面唯一的中文短行
        val fallback = lines.firstOrNull { isMerchantValue(it) }
        return fallback?.trim() ?: FALLBACK_MERCHANT
    }

    private fun isMerchantValue(value: String): Boolean {
        val v = value.trim().trim(':', '：', '-', '—', '·', ' ', '　')
        if (v.length !in 2..40) return false
        if (v.contains('¥') || v.contains('￥')) return false
        if (v.all { it.isDigit() || it == '.' || it == ',' }) return false
        if (v.count { it.isDigit() } > 6) return false
        if (!v.any { it.isChinese() }) return false
        if (isNoise(v)) return false
        if (MERCHANT_REJECT_WORDS.any { v.contains(it) }) return false
        if (PAYMENT_METHOD_WORDS.any { v.contains(it) }) return false
        return true
    }

    private fun isNoise(line: String): Boolean {
        val compact = compactLine(line)
        if (ALL_STATUS_PHRASES.any { compact.contains(it) }) return true
        if (NOISE_WORDS.any { line.contains(it) || compact.contains(it.lowercase()) }) return true
        if (PAYMENT_METHOD_WORDS.any { line.contains(it) || compact.contains(it.lowercase()) }) return true
        if (STATUS_LABELS.any { line == it || line == "$it：" || line == "$it:" }) return true
        if (MERCHANT_LABELS.any { line == it || line == "$it：" || line == "$it:" }) return true
        return false
    }

    private fun isAmountLike(line: String): Boolean {
        val sanitized = sanitizeAmountLine(line)
        return AMOUNT_REGEX.containsMatchIn(sanitized) &&
            (line.contains('.') || line.contains('¥') || line.contains('￥'))
    }

    private fun Char.isChinese(): Boolean = this in '一'..'鿿'

    // ------------------------------------------------------------ 收支/分类

    private fun detectType(text: String, status: PaymentStatus): BillType {
        if (status == PaymentStatus.REFUND_SUCCESS) return BillType.INCOME

        val income = listOf(
            "收款成功", "收款到账", "到账成功", "已到账", "已收款",
            "已存入零钱", "存入零钱", "收入",
            "received successfully", "received", "collection successful", "income"
        ).any { text.contains(it, ignoreCase = true) }

        val expense = listOf(
            "支付成功", "付款成功", "交易成功", "转账成功", "支付完成", "付款完成",
            "已支付", "已付款", "扣款", "消费", "付款给", "转账给",
            "payment successful", "payment success", "payment completed",
            "paid", "transfer successful", "purchase"
        ).any { text.contains(it, ignoreCase = true) }

        // 「收款方」只是页面字段，不代表当前用户是收款人
        return if (income && !expense) BillType.INCOME else BillType.EXPENSE
    }

    private fun guessCategory(text: String, type: BillType): String {
        if (type == BillType.INCOME) {
            return when {
                text.contains("退款") -> CategoryIds.REFUND
                text.contains("工资") || text.contains("薪资") -> CategoryIds.SALARY
                text.contains("利息") || text.contains("理财") -> CategoryIds.INVESTMENT
                else -> CategoryIds.OTHER_INCOME
            }
        }
        return when {
            containsAny(text, "医院", "药", "医疗", "挂号") -> CategoryIds.MEDICAL
            containsAny(text, "地铁", "公交", "滴滴", "打车", "加油", "停车", "高铁", "火车", "机票") -> CategoryIds.TRANSPORT
            containsAny(text, "淘宝", "天猫", "京东", "拼多多", "商城", "购物", "超市", "便利店", "商场") -> CategoryIds.SHOPPING
            containsAny(text, "电影", "游戏", "娱乐", "KTV", "视频", "会员") -> CategoryIds.FUN
            containsAny(text, "房租", "水费", "电费", "燃气", "物业") -> CategoryIds.HOME
            containsAny(text, "书店", "图书", "课程", "培训", "学费", "教育") -> CategoryIds.STUDY
            containsAny(text, "话费", "流量", "宽带", "通信", "手机") -> CategoryIds.COMMUNICATION
            containsAny(text, "宠物", "猫粮", "狗粮", "兽医") -> CategoryIds.PET
            containsAny(text, "餐", "饭", "宴", "酒楼", "火锅", "面馆", "外卖", "美团", "饿了么", "咖啡", "奶茶", "小吃", "烧烤") -> CategoryIds.FOOD
            else -> CategoryIds.OTHER_EXPENSE
        }
    }

    private fun containsAny(text: String, vararg words: String): Boolean =
        words.any { text.contains(it) }

    /**
     * 去重 key 两级策略：
     * 1. 页面带交易单号 → key 只由「包名 + 单号」决定，跨时间、跨重启都稳定，
     *    同一笔交易重复打开由数据库 smsHash 唯一索引直接 IGNORE；
     * 2. 没有单号（支付成功页常见）→ 回退到「包名 + 金额 + 3 分钟时间桶」，
     *    防无障碍事件刷屏；更长时间窗的重复由服务层入库前的金额查重兜底。
     * 刻意不把商户名放进 key：支付成功页是分步加载的，第一次事件可能还没有商户名，
     * 若把商户名计入会导致「无商户」和「有商户」各记一笔。
     */
    fun dedupeKey(packageName: String, payment: ScreenPayment, now: Long): String {
        val raw = payment.txnNo?.let { "screenTxn|$packageName|$it" }
            ?: "$packageName|${payment.amountCents}|${now / (3 * 60 * 1000)}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder("screen:")
        for (b in digest) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
