package br.com.windel.pos.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import br.com.windel.pos.data.entities.PaymentEntity

@Dao
interface PaymentResponseDao {
    @Query("SELECT * FROM payments")
    fun getAll(): List<PaymentEntity>

    @Insert
    suspend fun insert(vararg payments: PaymentEntity)

    @Delete
    suspend fun delete(payment: PaymentEntity)
}