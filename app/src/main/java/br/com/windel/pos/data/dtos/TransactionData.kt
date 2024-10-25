package br.com.windel.pos.data.dtos

data class TransactionData(
    var terminalSerial: String? = null,
    var flag: String?  = null,
    var transactionType: String?  = null,
    var authorization: String?  = null,
    var nsu: String?  = null,
    var orderId: String?  = null,
    var error: String?  = null
)