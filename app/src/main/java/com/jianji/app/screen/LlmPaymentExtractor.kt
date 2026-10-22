package com.jianji.app.screen

import android.content.Context
import android.util.Log
import com.jianji.app.BuildConfig
import com.jianji.app.data.model.BillType
import com.jianji.app.util.MoneyUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 本地规则识别失败时的 AI 兜底。
 *
 * 只发送页面文字，不发送截图；Key 从 App 私有目录的 maas.properties 读取，
 * 不进入 APK。
 */
object LlmPaymentExtractor {

    private const val TAG = "LlmExtractor"
    private const val MAX_INPUT_CHARS = 4000
    private const val MAX_TOKENS = 4096

    private val SYSTEM_PROMPT = """
        你是支付页面信息抽取器。只输出一个 JSON 对象，不要解释、Markdown、代码块。
        字段：isPayment(bool), status(SUCCESS|FAILED|PENDING|REFUND_SUCCESS|UNKNOWN), amount(字符串，单位元), merchant(字符串), type(EXPENSE|INCOME), deferred(bool), confidence(0到1)。
        规则：
        1. 普通支付成功页：status=SUCCESS，amount=实付/扣款金额。
        2. 先用后付/先享后付：如果页面有“下单成功/订单提交成功/订单已提交”，status=SUCCESS，deferred=true，amount 取“商品金额/订单金额”，绝对不要取“实付 0.00”。
        3. 只有“先用后付/0元下单”但没有下单成功：isPayment=false，status=PENDING。
        4. 当前交易的退款详情：status=REFUND_SUCCESS，type=INCOME。
        5. 订单列表/历史记录里的退款文案：isPayment=false，不要记收入。
        6. 失败/待支付页：isPayment=false，status=FAILED/PENDING。
        7. 无法确定：isPayment=false，status=UNKNOWN。
        只根据页面文字判断，不要编造。
    """.trimIndent()

    private val ORDER_SUCCESS_WORDS = listOf(
        "下单成功", "订单提交成功", "订单已提交", "订单创建成功"
    )

    suspend fun extract(
        context: Context,
        packageName: String,
        texts: List<String>
    ): ScreenPayment? = withContext(Dispatchers.IO) {
        if (!BuildConfig.DEBUG) return@withContext null
        val config = MaaSConfig.load(context) ?: return@withContext null

        val safeText = prepareText(texts)
        if (safeText.isEmpty()) return@withContext null

        val response = post(
            config = config,
            userContent = "页面文字：\n$safeText"
        ) ?: return@withContext null

        parsePayment(packageName, safeText, response)
    }

    private fun prepareText(texts: List<String>): String {
        val joined = texts.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(120)
            .joinToString("\n")
            .take(MAX_INPUT_CHARS)
        return redact(joined)
    }

    private fun redact(text: String): String = text
        .replace(Regex("""1[3-9]\d{9}"""), "[手机号]")
        .replace(Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"""), "[邮箱]")
        .replace(Regex("""\d{10,}"""), "[数字已脱敏]")

    private fun post(config: MaaSConfig.Config, userContent: String): String? {
        return try {
            val body = JSONObject().apply {
                put("model", config.model)
                put("max_tokens", MAX_TOKENS)
                put("temperature", 0)
                put("system", SYSTEM_PROMPT)
                put(
                    "messages",
                    JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", userContent)
                        }
                    )
                )
            }.toString()

            val conn = URL("${config.baseUrl}/v1/messages").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 8_000
                conn.readTimeout = 60_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("x-api-key", config.apiKey)
                conn.setRequestProperty("anthropic-version", "2023-06-01")
                // 用 \uXXXX 发送，避免网关把中文按 Latin-1 解码成乱码
                conn.outputStream.use {
                    it.write(asciiJson(body).toByteArray(Charsets.US_ASCII))
                }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val response = stream?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
                if (code !in 200..299) {
                    Log.w(TAG, "HTTP $code ${response.take(200)}")
                    return null
                }
                response
            } finally {
                conn.disconnect()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "请求失败: ${t.javaClass.simpleName}")
            null
        }
    }

    private fun parsePayment(
        packageName: String,
        safeText: String,
        responseBody: String
    ): ScreenPayment? {
        return try {
            val content = JSONObject(responseBody).optJSONArray("content") ?: return null
            val text = buildString {
                for (index in 0 until content.length()) {
                    val item = content.optJSONObject(index) ?: continue
                    if (item.optString("type") == "text") {
                        if (isNotEmpty()) append('\n')
                        append(item.optString("text"))
                    }
                }
            }
            val jsonText = extractJsonObject(text) ?: return null
            val obj = JSONObject(jsonText)

            if (!obj.optBoolean("isPayment", false)) return null

            val deferred = obj.optBoolean("deferred", false)
            var status = parseStatus(obj.optString("status"))
            if (status == PaymentStatus.PENDING && deferred &&
                ORDER_SUCCESS_WORDS.any { safeText.contains(it) }
            ) {
                status = PaymentStatus.SUCCESS
            }
            if (status != PaymentStatus.SUCCESS && status != PaymentStatus.REFUND_SUCCESS) {
                return null
            }

            if (status == PaymentStatus.REFUND_SUCCESS) {
                if (ScreenPaymentParser.isMerchantApp(packageName)) return null
                val historyHint = listOf("将退到", "预计", "历史", "列表")
                    .any { safeText.contains(it) }
                val refundEvidence = listOf(
                    "退款单号", "退款金额", "原路退回", "退款到账", "退款成功"
                ).any { safeText.contains(it) }
                if (historyHint || !refundEvidence) return null
            }

            val confidence = obj.optDouble("confidence", 0.0)
            if (confidence < 0.5) return null

            val amountText = obj.opt("amount")?.toString().orEmpty()
            val cents = MoneyUtils.parseToCents(amountText) ?: return null

            val type = if (obj.optString("type").equals("INCOME", true)) {
                BillType.INCOME
            } else {
                BillType.EXPENSE
            }

            val merchantRaw = obj.optString("merchant").trim()
            val merchant = if (merchantRaw.isEmpty() || merchantRaw.contains('�')) {
                ScreenPaymentParser.packageMerchant(packageName) ?: "屏幕识别"
            } else {
                merchantRaw.take(40)
            }

            ScreenPayment(
                amountCents = cents,
                type = type,
                categoryId = ScreenPaymentParser.categoryFor(safeText, type),
                merchant = merchant,
                rawText = safeText.take(300),
                app = PaymentApp.fromPackage(packageName),
                status = status,
                deferred = deferred
            )
        } catch (t: Throwable) {
            Log.w(TAG, "解析失败: ${t.javaClass.simpleName}")
            null
        }
    }

    private fun parseStatus(raw: String): PaymentStatus = when (raw.uppercase()) {
        "SUCCESS" -> PaymentStatus.SUCCESS
        "REFUND_SUCCESS" -> PaymentStatus.REFUND_SUCCESS
        "FAILED" -> PaymentStatus.FAILED
        "PENDING" -> PaymentStatus.PENDING
        else -> PaymentStatus.UNKNOWN
    }

    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return text.substring(start, end + 1)
    }

    private fun asciiJson(json: String): String = buildString(json.length + 128) {
        for (ch in json) {
            if (ch.code > 127) {
                append("\\u")
                append(ch.code.toString(16).padStart(4, '0'))
            } else {
                append(ch)
            }
        }
    }
}
