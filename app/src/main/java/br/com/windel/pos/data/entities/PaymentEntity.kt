package br.com.windel.pos.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "payments")
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "status")
    var status: String?,

    @ColumnInfo(name = "terminalSerial")
    var terminalSerial: String? = null,

    @ColumnInfo(name = "flag")
    var flag: String?  = null,

    @ColumnInfo(name = "transactionType")
    var transactionType: String?  = null,

    @ColumnInfo(name = "authorization")
    var authorization: String?  = null,

    @ColumnInfo(name = "nsu")
    var nsu: String?  = null,

    @ColumnInfo(name = "orderId")
    var orderId: String?  = null,

    @ColumnInfo(name = "error")
    var error: String?  = null

)