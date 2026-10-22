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
}
