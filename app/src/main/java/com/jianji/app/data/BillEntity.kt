package com.jianji.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 账单表。
 *
 * 金额使用「分」为单位的 Long 存储，彻底避免浮点误差。
 * smsHash 加唯一索引：同一条短信被重复投递时，第二次插入会被 IGNORE，
 * 从数据库层面保证不会重复记账。
 */
@Entity(
    tableName = "bills",
    indices = [
        Index("date"),
        Index(value = ["smsHash"], unique = true)
    ]
)
data class BillEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** EXPENSE / INCOME */
    val type: String,

    /** 金额，单位：分 */
    val amountCents: Long,

    val categoryId: String,

    val note: String = "",

    /** 账单日期，当天 00:00 的毫秒时间戳 */
    val date: Long,

    /** MANUAL / SMS / SCREEN */
    val source: String = SOURCE_MANUAL,

    /** 短信或屏幕识别的去重指纹，手动记账为 null */
    val smsHash: String? = null,

    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val SOURCE_MANUAL = "MANUAL"
        const val SOURCE_SMS = "SMS"
        const val SOURCE_SCREEN = "SCREEN"
    }
}
