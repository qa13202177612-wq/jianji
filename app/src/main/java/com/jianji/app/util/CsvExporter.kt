package com.jianji.app.util

import android.content.Context
import android.net.Uri
import com.jianji.app.data.BillEntity
import com.jianji.app.data.model.BillType
import com.jianji.app.data.model.Categories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CsvExporter {

    /**
     * 生成 CSV 文本。
     * 开头写入 UTF-8 BOM，否则 Excel 打开中文会乱码。
     */
    fun build(bills: List<BillEntity>): String {
        val sb = StringBuilder("\uFEFF")
        sb.append("日期,类型,分类,金额,备注,来源\r\n")
        bills.sortedWith(compareByDescending<BillEntity> { it.date }.thenByDescending { it.id })
            .forEach { bill ->
                val type = BillType.fromRaw(bill.type)
                val category = Categories.safeById(bill.categoryId, type)
                sb.append(escape(DateUtils.csvDate(DateUtils.localDate(bill.date)))).append(',')
                sb.append(type.label).append(',')
                sb.append(escape(category.name)).append(',')
                sb.append(MoneyUtils.plainCents(bill.amountCents)).append(',')
                sb.append(escape(bill.note)).append(',')
                sb.append(
                    when (bill.source) {
                        BillEntity.SOURCE_SMS -> "短信自动"
                        BillEntity.SOURCE_SCREEN -> "屏幕识别"
                        else -> "手动"
                    }
                )
                sb.append("\r\n")
            }
        return sb.toString()
    }

    /** 通过 SAF 返回的 Uri 写入，整个过程在 IO 线程，异常全部捕获 */
    suspend fun export(context: Context, uri: Uri, bills: List<BillEntity>): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val text = build(bills)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: error("无法打开目标文件")
                bills.size
            }
        }

    private fun escape(value: String): String {
        val v = value.replace("\r", " ").replace("\n", " ")
        return if (v.contains(',') || v.contains('"')) {
            "\"" + v.replace("\"", "\"\"") + "\""
        } else {
            v
        }
    }
}
