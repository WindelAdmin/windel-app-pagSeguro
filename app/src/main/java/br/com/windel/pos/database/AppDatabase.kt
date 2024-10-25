package br.com.windel.pos.database
import androidx.room.Database
import androidx.room.RoomDatabase
import br.com.windel.pos.data.entities.PaymentEntity
import br.com.windel.pos.database.dao.PaymentResponseDao

@Database(entities = [PaymentEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun paymentDao(): PaymentResponseDao
}