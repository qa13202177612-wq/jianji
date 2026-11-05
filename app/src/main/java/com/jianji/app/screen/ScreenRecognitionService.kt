package com.jianji.app.screen

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.jianji.app.BuildConfig
import com.jianji.app.JianJiApp
import com.jianji.app.MainActivity
import com.jianji.app.R
import com.jianji.app.data.BillEntity
import com.jianji.app.data.model.Categories
import com.jianji.app.util.MoneyUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * 屏幕内容识别服务（无障碍 + OCR 兜底）。
 *
 * 工作方式：
 * 1. 优先读取当前所有窗口的文本节点（速度快、无截图）；
 * 2. 如果支付页屏蔽了无障碍文本，或文本里只有图片，使用 Android 11+ 的
 *    AccessibilityService.takeScreenshot() 截一张图，交给 ML Kit 中文 OCR；
 * 3. 命中「支付成功/转账成功 + 金额」后解析金额、收支方向、商户；
 * 4. 写入 Room，并发送可点击通知，用户可进入 App 修改或删除。
 *
 * 不执行点击、不模拟输入、不上传图片；截图只在内存中处理，识别完立即回收。
 */
class ScreenRecognitionService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recent = ConcurrentHashMap<String, Long>()
    private val lastContentEventAt = ConcurrentHashMap<String, Long>()

    // 截图 + OCR 开销较大：按包名做冷却和并发保护，避免普通聊天页反复触发。
    private val lastOcrAt = ConcurrentHashMap<String, Long>()
    private val lastOcrFailed = ConcurrentHashMap<String, Boolean>()
    private val lastOcrPage = ConcurrentHashMap<String, String>()
    private val ocrInFlight = ConcurrentHashMap.newKeySet<String>()

    // 千问兜底：本地规则失败时调用，按包名限流。
    private val lastLlmAt = ConcurrentHashMap<String, Long>()
    private val llmInFlight = ConcurrentHashMap.newKeySet<String>()
    private val llmSelfTestDone = AtomicBoolean(false)

    /** 只有 debug 包才输出逐页分析日志，避免正式包刷 logcat。 */
    private val isDebuggable by lazy {
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    private val recognizerDelegate = lazy<TextRecognizer> {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }
    private val recognizer: TextRecognizer by recognizerDelegate

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            serviceInfo = serviceInfo?.apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                notificationTimeout = 300
                flags = flags or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
            }
            createChannel()
            runLlmSelfTest()
        } catch (t: Throwable) {
            Log.e(TAG, "初始化屏幕识别服务失败", t)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event == null) return
            val packageName = event.packageName?.toString().orEmpty()
            if (isDebuggable &&
                (packageName == this.packageName || PAYMENT_PACKAGES.contains(packageName))
            ) {
                Log.i(
                    TAG,
                    "收到事件 type=${event.eventType} package=$packageName " +
                        "class=${event.className} textCount=${event.text?.size ?: 0}"
                )
            }
            val type = event.eventType
            if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            ) {
                return
            }

            // 只对高频的 CONTENT_CHANGED 做轻量节流；WINDOW_STATE_CHANGED 必须处理。
            // 支付成功页通常是静态页：如果页面切换的那一次事件被吞掉，后面不会再有事件。
            val now = SystemClock.elapsedRealtime()
            if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                val last = lastContentEventAt[packageName] ?: 0L
                if (now - last < 300L) return
                lastContentEventAt[packageName] = now
            }

            val className = event.className?.toString().orEmpty()
            val isWindowStateChange = type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            // 新页面出现时清掉上一页的 OCR 失败冷却，避免登录页/首页识别失败后
            // 30 秒内进入支付成功页却不再尝试截图。
            if (isWindowStateChange) {
                lastOcrFailed.remove(packageName)
            }
            val texts = collectTexts(event, packageName)

            val analysis = ScreenPaymentParser.analyze(texts, packageName)
            if (isDebuggable &&
                (PAYMENT_PACKAGES.contains(packageName) ||
                    analysis.status != PaymentStatus.UNKNOWN)
            ) {
                Log.d(
                    TAG,
                    "页面分析 package=$packageName class=$className texts=${texts.size} " +
                        "app=${analysis.app} status=${analysis.status} " +
                        "evidence=${analysis.hasPaymentEvidence} reason=${analysis.reason}"
                )
            }
            val payment = analysis.payment
            if (payment != null && analysis.isRecordable) {
                handlePayment(packageName, payment)
                return
            }

            // 页面已经明确是失败/待支付：截图也不会变成成功，直接跳过，省电也避免误记。
            if (analysis.status == PaymentStatus.FAILED ||
                analysis.status == PaymentStatus.PENDING
            ) {
                Log.d(
                    TAG,
                    "跳过非成功支付页 package=$packageName status=${analysis.status} (${analysis.reason})"
                )
                return
            }

            // 本地规则没有拿到完整账单时，尝试 AI 兜底（仅 debug 且已配置）
            if (payment == null) {
                tryLlmFallback(packageName, texts)
            }

            // 无障碍读不到（微信/支付宝常见）：用无障碍截图 + ML Kit OCR 兜底
            if (shouldTryOcr(packageName, className, texts, analysis, isWindowStateChange)) {
                scope.launch {
                    try {
                        // 稍等支付页动画完成；时间不能太长，否则用户可能已经离开成功页
                        delay(200)
                        val beforePackage = activeWindowPackage()
                        // 只有切换到另一个支付 App（或简记测试页）时才可能造成张冠李戴；
                        // systemui/桌面只会在 OCR 里得到无关文本，不会误记。
                        val foreignPaymentPage = beforePackage != null &&
                            beforePackage != packageName &&
                            (PAYMENT_PACKAGES.contains(beforePackage) ||
                                beforePackage == this@ScreenRecognitionService.packageName)
                        if (foreignPaymentPage) {
                            Log.d(TAG, "跳过 OCR：页面已切换 expected=$packageName actual=$beforePackage")
                            return@launch
                        }
                        Log.d(TAG, "开始 OCR package=$packageName")
                        val bitmap = captureScreenshot()
                        if (bitmap == null) {
                            Log.d(TAG, "截图失败 package=$packageName")
                            return@launch
                        }
                        val ocrLines = try {
                            recognize(bitmap)
                        } finally {
                            bitmap.recycle()
                        }
                        if (isDebuggable && PAYMENT_PACKAGES.contains(packageName)) {
                            Log.d(
                                TAG,
                                "OCR 文本 package=$packageName active=${activeWindowPackage()} " +
                                    "lines=${ocrLines.take(24)}"
                            )
                        }
                        val ocrAnalysis = ScreenPaymentParser.analyze(ocrLines, packageName)
                        val ocrPayment = ocrAnalysis.payment
                        if (ocrAnalysis.isRecordable && ocrPayment != null) {
                            lastOcrFailed[packageName] = false
                            handlePayment(packageName, ocrPayment)
                        } else {
                            lastOcrFailed[packageName] = true
                            tryLlmFallback(packageName, ocrLines)
                            Log.d(
                                TAG,
                                "OCR 未命中 package=$packageName status=${ocrAnalysis.status} " +
                                    "(${ocrAnalysis.reason})"
                            )
                        }
                    } catch (t: Throwable) {
                        lastOcrFailed[packageName] = true
                        Log.e(TAG, "OCR 识别失败 package=$packageName", t)
                    } finally {
                        ocrInFlight.remove(packageName)
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "屏幕识别失败", t)
        }
    }

    private fun shouldTryOcr(
        packageName: String,
        className: String,
        texts: List<String>,
        analysis: ScreenAnalysis,
        isWindowStateChange: Boolean
    ): Boolean {
        fun skip(reason: String): Boolean {
            if (isDebuggable) {
                Log.i(TAG, "OCR 跳过[$reason] package=$packageName class=$className")
            }
            return false
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return skip("sdk<30")
        if (packageName == this.packageName && !className.contains("ScreenDemoActivity")) {
            return skip("own-package")
        }
        if (analysis.status == PaymentStatus.FAILED ||
            analysis.status == PaymentStatus.PENDING
        ) {
            return skip("status=${analysis.status}")
        }

        val isDemo = className.contains("ScreenDemoActivity")
        val isPaymentApp = PAYMENT_PACKAGES.contains(packageName)
        if (!isDemo && !isPaymentApp && !looksLikePaymentPage(texts)) {
            return skip("not-payment-page")
        }

        // 微信/支付宝里绝大多数事件都来自聊天和列表页。只有三种情况值得截图：
        // 1) 已经读到「支付成功」但没读到金额（金额很可能是图片）；
        // 2) 无障碍几乎读不到文字，或页面文本很少（微信支付页常见）；
        // 3) 页面文本本身已经像支付页/交易详情。
        val pageKey = pageFingerprint(className, texts)
        var pageWorthOcr = isDemo
        if (isPaymentApp && !isDemo) {
            val joined = texts.joinToString(" ")
            val looksOpaque = texts.size <= 6 || joined.length <= 40
            val sparsePage = texts.size <= 12
            // 微信/支付宝的支付成功页经常是一个新的窗口/页面，文本可能多达几十条，
            // 但无障碍文本里没有「支付成功」字样，只能靠 OCR。
            val windowStatePage = isWindowStateChange && texts.size <= 40
            val statusNeedsAmount = analysis.status == PaymentStatus.SUCCESS &&
                analysis.payment == null
            pageWorthOcr = statusNeedsAmount || looksOpaque || sparsePage ||
                windowStatePage || looksLikePaymentPage(texts)
            if (!pageWorthOcr) return skip("not-opaque-and-no-signal")
        }

        val now = SystemClock.elapsedRealtime()
        val sinceLast = now - (lastOcrAt[packageName] ?: 0L)
        val pageChanged = lastOcrPage[packageName] != pageKey
        // 强信号页面切换，或页面内容已经变化为另一个值得识别的页面时，
        // 不再被上一页的失败冷却挡住。微信支付成功页常常只发 CONTENT_CHANGED，
        // 不重置就会一直停在上一页的 cooldown 里。
        val strongTrigger = isDemo ||
            analysis.status == PaymentStatus.SUCCESS ||
            analysis.hasPaymentEvidence ||
            looksLikePaymentPage(texts)
        val bypassCooldown = (isWindowStateChange && strongTrigger) ||
            (pageChanged && pageWorthOcr && sinceLast >= OCR_PAGE_CHANGE_MIN_MS)
        if (!bypassCooldown) {
            val cooldown = if (lastOcrFailed[packageName] == true) {
                OCR_MISS_COOLDOWN_MS
            } else {
                OCR_COOLDOWN_MS
            }
            if (sinceLast < cooldown) return skip("cooldown")
        }
        if (!ocrInFlight.add(packageName)) return skip("in-flight")
        lastOcrAt[packageName] = now
        lastOcrPage[packageName] = pageKey
        if (isDebuggable) {
            Log.i(TAG, "OCR 触发 package=$packageName class=$className texts=${texts.size}")
        }
        return true
    }

    private fun looksLikePaymentPage(texts: List<String>): Boolean {
        val joined = texts.joinToString(" ").lowercase()
        return PAYMENT_PAGE_SIGNALS.any { joined.contains(it.lowercase()) }
    }

    /** 页面内容的轻量指纹，用于识别微信/支付宝在同一 Activity 内切换页面。 */
    private fun pageFingerprint(className: String, texts: List<String>): String {
        val joined = texts.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
            .joinToString("\u0001")
        return "$className|${joined.hashCode()}|${joined.length}"
    }

    /** 只在后台线程调用，用于确认截图前后的前台包名没有变化。 */
    private fun activeWindowPackage(): String? = try {
        rootInActiveWindow?.packageName?.toString()
    } catch (_: Throwable) {
        null
    }

    /** 服务连接后用一段固定样例做一次自检，方便确认 Key、模型和网络是否可用。 */
    private fun runLlmSelfTest() {
        if (!BuildConfig.DEBUG) return
        if (!llmSelfTestDone.compareAndSet(false, true)) return
        if (MaaSConfig.load(applicationContext) == null) return

        scope.launch {
            val sample = listOf(
                "2:50",
                "下单成功",
                "先用后付",
                "确认收货后付款",
                "商品金额 ¥1.24",
                "实付 ¥0.00",
                "拼多多"
            )
            val result = LlmPaymentExtractor.extract(
                applicationContext,
                "com.xunmeng.pinduoduo",
                sample
            )
            Log.i(
                TAG,
                "LLM 自检: " + (
                    result?.let {
                        "amount=${it.amountCents} merchant=${it.merchant} " +
                            "deferred=${it.deferred}"
                    } ?: "未命中"
                )
            )
        }
    }

    private fun tryLlmFallback(packageName: String, texts: List<String>) {
        if (!BuildConfig.DEBUG) return
        if (!looksLikeLlmCandidate(texts)) return
        if (MaaSConfig.load(applicationContext) == null) return

        val now = SystemClock.elapsedRealtime()
        val last = lastLlmAt[packageName] ?: 0L
        if (now - last < LLM_COOLDOWN_MS) return
        if (!llmInFlight.add(packageName)) return
        lastLlmAt[packageName] = now

        scope.launch {
            try {
                val payment = LlmPaymentExtractor.extract(
                    applicationContext,
                    packageName,
                    texts
                )
                if (payment != null) {
                    Log.d(
                        TAG,
                        "LLM 兜底命中 package=$packageName amount=${payment.amountCents} " +
                            "merchant=${payment.merchant} deferred=${payment.deferred}"
                    )
                    handlePayment(packageName, payment)
                } else {
                    Log.d(TAG, "LLM 兜底未命中 package=$packageName")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "LLM 兜底失败 package=$packageName", t)
            } finally {
                llmInFlight.remove(packageName)
            }
        }
    }

    private fun looksLikeLlmCandidate(texts: List<String>): Boolean {
        val joined = texts.joinToString(" ").lowercase()
        return LLM_PAGE_HINTS.any { joined.contains(it.lowercase()) }
    }

    private fun handlePayment(packageName: String, payment: ScreenPayment) {
        val now = SystemClock.elapsedRealtime()
        val key = ScreenPaymentParser.dedupeKey(packageName, payment, System.currentTimeMillis())
        val last = recent[key]
        if (last != null && now - last < 3 * 60 * 1000L) {
            Log.d(TAG, "跳过重复账单 package=$packageName amount=${payment.amountCents}")
            return
        }
        recent[key] = now
        if (recent.size > 60) {
            recent.entries.removeIf { now - it.value > 10 * 60 * 1000L }
        }
        saveBill(payment, key, packageName)
    }

    private fun collectTexts(event: AccessibilityEvent, packageName: String): List<String> {
        val texts = ArrayList<String>(48)
        // 只对微信/支付宝/本应用读取节点树。其他 App（系统 UI、桌面、后台应用）
        // 只用事件自带文本，避免 getChild() 卡住无障碍回调线程，拖死后续支付页事件。
        val shouldReadWindow = packageName == this.packageName ||
            PAYMENT_PACKAGES.contains(packageName)
        if (shouldReadWindow) {
            val deadline = SystemClock.elapsedRealtime() + 200L
            try {
                collectText(rootInActiveWindow, texts, 0, deadline)
            } catch (_: Throwable) {
            }
        }
        try {
            event.text?.forEach { charSequence ->
                charSequence?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(texts::add)
            }
        } catch (_: Throwable) {
        }
        try {
            event.contentDescription?.toString()?.trim()
                ?.takeIf { it.isNotEmpty() }?.let(texts::add)
        } catch (_: Throwable) {
        }
        return texts.distinct()
    }

    private fun collectText(
        node: AccessibilityNodeInfo?,
        out: MutableList<String>,
        depth: Int,
        deadline: Long
    ) {
        if (node == null || depth > 30 || out.size > 200) return
        if (SystemClock.elapsedRealtime() > deadline) return
        try {
            node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
            node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
            node.hintText?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                node.tooltipText?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
            }

            for (index in 0 until node.childCount) {
                if (SystemClock.elapsedRealtime() > deadline) break
                collectText(node.getChild(index), out, depth + 1, deadline)
            }
        } catch (t: Throwable) {
            // 个别节点在窗口切换时会失效，忽略即可
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun captureScreenshot(): Bitmap? = suspendCancellableCoroutine { continuation ->
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        val buffer = screenshot.hardwareBuffer
                        val bitmap = try {
                            Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)
                        } catch (t: Throwable) {
                            null
                        } finally {
                            try {
                                buffer.close()
                            } catch (_: Throwable) {
                            }
                        }
                        if (continuation.isActive) continuation.resume(bitmap)
                    }

                    override fun onFailure(errorCode: Int) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            )
        } catch (t: Throwable) {
            if (continuation.isActive) continuation.resume(null)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun recognize(bitmap: Bitmap): List<String> = suspendCancellableCoroutine { continuation ->
        try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { result ->
                    val lines = result.textBlocks.flatMap { block ->
                        block.lines.map { line -> line.text }
                    }
                    if (continuation.isActive) continuation.resume(lines)
                }
                .addOnFailureListener { t ->
                    Log.e(TAG, "ML Kit OCR 失败", t)
                    if (continuation.isActive) continuation.resume(emptyList())
                }
        } catch (t: Throwable) {
            if (continuation.isActive) continuation.resume(emptyList())
        }
    }

    private fun saveBill(payment: ScreenPayment, dedupeKey: String, packageName: String) {
        val app = application as? JianJiApp ?: return
        scope.launch {
            try {
                // 防线3：入库前查库里近 24 小时是否已有同金额、同方向的自动账单。
                // 点开已支付订单/账单列表时页面文本和支付成功页不同，内存 key 与
                // 3 分钟时间桶都挡不住；同一笔交易若已由银行短信入账，这里也拦下。
                if (app.repository.hasRecentAutoBill(
                        payment.amountCents,
                        payment.type.name,
                        System.currentTimeMillis() - AUTO_DEDUPE_WINDOW_MS
                    )
                ) {
                    Log.d(
                        TAG,
                        "跳过重复账单(窗口内已记同金额) package=$packageName " +
                            "amount=${payment.amountCents} type=${payment.type} txn=${payment.txnNo}"
                    )
                    return@launch
                }
                val bill = BillEntity(
                    type = payment.type.name,
                    amountCents = payment.amountCents,
                    categoryId = payment.categoryId,
                    note = buildString {
                        append("屏幕识别 · ").append(payment.merchant)
                        if (payment.deferred) append("（先用后付）")
                    },
                    date = com.jianji.app.util.DateUtils.startOfDay(
                        java.time.LocalDate.now()
                    ),
                    source = BillEntity.SOURCE_SCREEN,
                    smsHash = dedupeKey
                )
                val id = app.repository.add(bill)
                if (id > 0L) {
                    Log.d(
                        TAG,
                        "记账成功 package=$packageName amount=${payment.amountCents} " +
                            "merchant=${payment.merchant} status=${payment.status} " +
                            "deferred=${payment.deferred}"
                    )
                    showRecordedNotification(payment)
                } else {
                    Log.d(TAG, "数据库忽略重复账单 package=$packageName key=$dedupeKey")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "保存屏幕识别账单失败 package=$packageName", t)
            }
        }
    }

    private fun showRecordedNotification(payment: ScreenPayment) {
        try {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                val suffix = if (payment.deferred) "（先用后付）" else ""
                showToast(
                    "简记已记账：${MoneyUtils.format(payment.amountCents)} ${payment.merchant}$suffix"
                )
                return
            }

            createChannel()
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val category = Categories.safeById(payment.categoryId, payment.type)
            val title = when {
                payment.deferred -> "简记自动记了一笔先用后付"
                payment.type.name == "INCOME" -> "简记自动记了一笔收入"
                else -> "简记自动记了一笔支出"
            }
            val deferredText = if (payment.deferred) " · 先用后付" else ""
            val text = "${category.emoji} ${category.name} · " +
                "${MoneyUtils.format(payment.amountCents)} · ${payment.merchant}$deferredText"

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()

            NotificationManagerCompat.from(this).notify(text.hashCode(), notification)
        } catch (t: Throwable) {
            Log.e(TAG, "发送记账通知失败", t)
        }
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            try {
                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            } catch (_: Throwable) {
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager?.getNotificationChannel(CHANNEL_ID) == null) {
                manager?.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "屏幕识别记账",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "识别到支付成功页并自动记账时提醒"
                    }
                )
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        try {
            if (recognizerDelegate.isInitialized()) {
                runCatching { recognizer.close() }
            }
            scope.cancel()
        } finally {
            super.onDestroy()
        }
    }

    companion object {
        private const val TAG = "ScreenRecognition"
        private const val CHANNEL_ID = "screen_recognition"

        /**
         * 这些 App 会直接展示支付成功页（含微信/支付宝免密支付后返回商户页），
         * 因此需要读取节点并在必要时 OCR。
         */
        private val PAYMENT_PACKAGES = setOf(
            "com.eg.android.AlipayGphone", // 支付宝
            "com.tencent.mm",              // 微信
            "com.unionpay",                // 云闪付
            // 常见内置收银台/免密支付场景
            "com.xunmeng.pinduoduo",       // 拼多多
            "com.taobao.taobao",           // 淘宝
            "com.jingdong.app.mall",       // 京东
            "com.sankuai.meituan",         // 美团
            "me.ele",                      // 饿了么
            "com.ss.android.ugc.aweme",    // 抖音(商城购物/团购券,2026-11-05)
            "com.taobao.idlefish"          // 闲鱼
        )

        /** 正常 OCR 间隔；识别失败后拉长间隔，避免在聊天页反复截图。 */
        private const val OCR_COOLDOWN_MS = 8_000L
        private const val OCR_MISS_COOLDOWN_MS = 10_000L
        /** 同一 App 内页面内容变化后，允许重新 OCR 的最小间隔。 */
        private const val OCR_PAGE_CHANGE_MIN_MS = 1_500L

        /** 本地规则失败后，AI 兜底的最小间隔。 */
        private const val LLM_COOLDOWN_MS = 15_000L

        /** 入库前「同金额+同方向自动账单」查重窗口：点开旧订单不再重复入账。 */
        private const val AUTO_DEDUPE_WINDOW_MS = 24 * 60 * 60 * 1000L

        /** 只有出现这些文案才值得调用 AI，避免商品页/聊天页误调用。 */
        private val LLM_PAGE_HINTS = listOf(
            "支付成功", "付款成功", "交易成功", "下单成功", "订单提交成功", "订单已提交",
            "实付", "扣款", "已扣款", "deducted", "payment successful", "payment success",
            "退款成功", "退款金额", "商品金额"
        )

        /** 只有出现这些「强支付语义」时才考虑截图，普通商品页的 ¥ 不算。 */
        private val PAYMENT_PAGE_SIGNALS = listOf(
            "支付成功", "付款成功", "交易成功", "转账成功",
            "支付失败", "付款失败", "交易关闭", "待支付", "待付款",
            "收款方", "收款人", "商户全称", "商户名称",
            "交易单号", "商户单号", "账单详情",
            "实付", "支付金额", "付款金额",
            "确认支付", "立即支付", "支付方式", "付款方式",
            // 免密支付 / 商户内置收银台
            "免密支付", "微信免密支付", "免密", "已支付", "支付完成",
            "订单支付成功", "付款成功", "微信支付", "下单成功", "deducted",
            "先用后付", "先享后付", "0元下单", "确认收货后付款",
            // 英文支付页 / 小程序页面
            "payment successful", "payment success", "payment completed",
            "payment failed", "weixin pay", "pay now", "confirm payment"
        )
    }
}
