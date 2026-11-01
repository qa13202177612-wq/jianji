package com.jianji.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {

    @Query("SELECT * FROM bills ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills ORDER BY date DESC, id DESC")
    suspend fun getAll(): List<BillEntity>

    @Query("SELECT * FROM bills WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): BillEntity?

    /** 返回 -1 表示触发唯一索引冲突（短信重复） */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bill: BillEntity): Long

    @Update
    suspend fun update(bill: BillEntity)

    @Delete
    suspend fun delete(bill: BillEntity)

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM bills")
    suspend fun count(): Int

    /**
     * 时间窗内是否已有同金额、同方向的自动账单（屏幕识别/短信）。
     * 用于防止用户点开已支付订单、账单列表时重复入账。
     */
    @Query(
        "SELECT COUNT(*) FROM bills " +
            "WHERE amountCents = :amountCents AND type = :type " +
            "AND source IN ('SCREEN', 'SMS') AND createdAt > :sinceMillis"
    )
    suspend fun countRecentAutoBills(amountCents: Long, type: String, sinceMillis: Long): Int
}
