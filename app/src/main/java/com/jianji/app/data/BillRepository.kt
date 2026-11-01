package com.jianji.app.data

import kotlinx.coroutines.flow.Flow

class BillRepository(private val dao: BillDao) {

    val bills: Flow<List<BillEntity>> = dao.observeAll()

    suspend fun all(): List<BillEntity> = dao.getAll()

    suspend fun byId(id: Long): BillEntity? = dao.getById(id)

    suspend fun add(bill: BillEntity): Long = dao.insert(bill)

    suspend fun update(bill: BillEntity) = dao.update(bill)

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun count(): Int = dao.count()

    /** 时间窗内是否已有一笔同金额同方向的自动账单（防重复入账）。 */
    suspend fun hasRecentAutoBill(amountCents: Long, type: String, sinceMillis: Long): Boolean =
        dao.countRecentAutoBills(amountCents, type, sinceMillis) > 0
}
