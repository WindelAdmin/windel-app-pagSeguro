package br.com.windel.pos.data.dtos

data class TransactionResultData(
    var terminalSerial: String? = null,
    var flag: String?  = null,
    var transactionType: String?  = null,
    var authorization: String?  = null,
    var nsu: String?  = null,
    var orderId: String?  = null,
    var transactionCode: String? = null,
    var transactionIdInTerminal: String? = null,
    var error: String?  = null
)