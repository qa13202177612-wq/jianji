package com.jianji.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import android.widget.Toast
import com.jianji.app.JianJiApp
import com.jianji.app.data.BillEntity
import com.jianji.app.data.model.Categories
import com.jianji.app.util.MoneyUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 短信自动记账入口。
 *
 * 注意：
 * - onReceive 在主线程且只有约 10 秒生命周期，因此用 goAsync() + 协程处理；
 * - 所有异常都被捕获并记录日志，解析失败不会导致应用或系统崩溃；
 * - 未授予 RECEIVE_SMS 时系统不会投递广播，这里无需额外判断权限。
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val app = context.applicationContext as? JianJiApp ?: return
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                if (!app.settings.snapshot().autoSms) return@launch

                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (messages.isNullOrEmpty()) return@launch

                // 多段短信按发送方合并，避免一条长短信被拆成多条账单
                val grouped = messages.filterNotNull()
                    .groupBy { it.originatingAddress.orEmpty() }

                for ((sender, parts) in grouped) {
                    val body = parts.joinToString(separator = "") { it.messageBody.orEmpty() }
                    if (body.isBlank()) continue

                    val timestamp = parts.minOfOrNull { it.timestampMillis }
                        ?: System.currentTimeMillis()

                    val parsed = SmsParser.parse(body, sender, timestamp) ?: continue
                    val hash = SmsParser.hash(sender, body, timestamp)

                    val bill = BillEntity(
                        type = parsed.type.name,
                        amountCents = parsed.amountCents,
                        categoryId = parsed.categoryId,
                        note = parsed.note,
                        date = parsed.dateMillis,
                        source = BillEntity.SOURCE_SMS,
                        smsHash = hash
                    )

                    // 返回 -1 说明唯一索引命中，属于重复短信，直接跳过
                    val id = app.repository.add(bill)
                    if (id > 0L) {
                        val category = Categories.safeById(parsed.categoryId, parsed.type)
                        showToast(
                            context,
                            "简记已自动记账：${parsed.type.label} ${category.name} ${MoneyUtils.format(parsed.amountCents)}"
                        )
                    }
                }
            } catch (t: Throwable) {
                // 关键：任何异常都不能外抛
                Log.e(TAG, "短信自动记账失败", t)
            } finally {
                try {
                    pendingResult.finish()
                } catch (_: Throwable) {
                    // 忽略
                }
                scope.cancel()
            }
        }
    }

    private fun showToast(context: Context, message: String) {
        try {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        } catch (_: Throwable) {
            // 后台限制 Toast 时忽略
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
