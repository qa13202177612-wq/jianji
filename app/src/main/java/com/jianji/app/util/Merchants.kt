package com.jianji.app.util

import com.jianji.app.data.BillEntity

/**
 * 从账单反推「商家名」,用于商家消费排行。
 *
 * 三种来源的备注格式:
 * - 屏幕识别:"屏幕识别 · 瑞幸咖啡" / "屏幕识别 · 拼多多（先用后付）"
 * - 短信:"短信 · 在美团消费" / 银行正文摘要 → 尽量抽「在XX消费」里的 XX
 * - 手动:用户自己填的备注,短备注直接当商家用,过长/为空归入未标注
 */
object Merchants {

    const val UNKNOWN = "未标注商家"

    private val SMS_MERCHANT = Regex("""在(.{2,16}?)(?:的)?(?:消费|购买|付款|刷卡|支付)""")

    fun resolve(bill: BillEntity): String {
        val note = bill.note.trim()

        if (bill.source == BillEntity.SOURCE_SCREEN) {
            val tail = note.substringAfter("·", "").trim()
                .removeSuffix("（先用后付）")
                .trim()
            if (tail.isNotEmpty() && tail != "屏幕识别") return tail
            return UNKNOWN
        }

        if (bill.source == BillEntity.SOURCE_SMS) {
            SMS_MERCHANT.find(note)?.groupValues?.get(1)?.trim()?.let {
                if (it.isNotEmpty()) return it
            }
            return "银行短信"
        }

        // 手动记账:短备注视为商家/用途,长的截断,空的归未标注
        if (note.isEmpty()) return UNKNOWN
        return if (note.length <= 10) note else note.take(10)
    }
}
