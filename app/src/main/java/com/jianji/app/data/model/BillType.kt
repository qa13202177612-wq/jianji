package com.jianji.app.data.model

/** 收支类型。数据库中存字符串，避免枚举序号变化导致历史数据错乱。 */
enum class BillType(val label: String) {
    EXPENSE("支出"),
    INCOME("收入");

    companion object {
        fun fromRaw(raw: String?): BillType =
            if (raw == INCOME.name) INCOME else EXPENSE
    }
}
