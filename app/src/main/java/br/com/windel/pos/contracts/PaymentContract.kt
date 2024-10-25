package br.com.windel.pos.contracts

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import br.com.windel.pos.Utils
import br.com.windel.pos.data.dtos.DataPayment
import br.com.windel.pos.data.dtos.DataPaymentResponse
import br.com.windel.pos.data.dtos.TransactionData
import br.com.windel.pos.enums.EventsEnum.EVENT_CANCELED
import br.com.windel.pos.enums.EventsEnum.EVENT_FAILED
import br.com.windel.pos.enums.EventsEnum.EVENT_SUCCESS
import br.com.windel.pos.enums.IntentEnum.AUTHORIZATION_LABEL
import br.com.windel.pos.enums.IntentEnum.DAY_LABEL
import br.com.windel.pos.enums.IntentEnum.ERROR_LABEL
import br.com.windel.pos.enums.IntentEnum.EXPIRATION_DATE_LABEL
import br.com.windel.pos.enums.IntentEnum.FLAG_LABEL
import br.com.windel.pos.enums.IntentEnum.INSTALLMENTS_LABEL
import br.com.windel.pos.enums.IntentEnum.NSU_LABEL
import br.com.windel.pos.enums.IntentEnum.PRINT_VOUCHER
import br.com.windel.pos.enums.IntentEnum.SERIAL_LABEL
import br.com.windel.pos.enums.IntentEnum.TRANSACTION_LABEL
import br.com.windel.pos.enums.IntentEnum.TRANSACTION_VALUE_LABEL
import br.com.windel.pos.enums.IntentEnum.VERO_PACKAGE

class PaymentContract : ActivityResultContract<DataPayment, DataPaymentResponse>() {
    var intentIsActive = false
    override fun createIntent(context: Context, transactionData: DataPayment): Intent {

        intentIsActive = true;

        val intent = Intent(VERO_PACKAGE.value)

        intent.putExtra(TRANSACTION_LABEL.value, transactionData.transactionType)

        if (transactionData.transactionValue?.isNotEmpty() == true){
            intent.putExtra(TRANSACTION_VALUE_LABEL.value, Utils().convertToIntVero(transactionData.transactionValue))
        }

        transactionData.installments?.let {
            intent.putExtra(INSTALLMENTS_LABEL.value, it)
        }

        transactionData.dayOfMonth?.let {
            intent.putExtra(DAY_LABEL.value, it)
        }

        transactionData.expirationDate?.let {
            intent.putExtra(EXPIRATION_DATE_LABEL.value, it)
        }

        intent.putExtra(PRINT_VOUCHER.value, true)

        return intent
    }

    override fun parseResult(resultCode: Int, data: Intent?): DataPaymentResponse {
        if (resultCode == Activity.RESULT_OK && data != null) {
            if (data.getStringExtra(AUTHORIZATION_LABEL.value) == null) {
                val transcationData = TransactionData()
                transcationData.error = data.getStringExtra(ERROR_LABEL.value)
               return DataPaymentResponse(EVENT_FAILED.value, transcationData)
            }
            return DataPaymentResponse(EVENT_SUCCESS.value, extractTransactionDataFromIntent(data))
        }else{
            return DataPaymentResponse(EVENT_CANCELED.value, TransactionData())
        }
    }

    private fun extractTransactionDataFromIntent(intent: Intent?): TransactionData? {
        if (intent == null) {1
            return null
        }

        val SERIAL = intent.getStringExtra(SERIAL_LABEL.value) ?: null
        val BANDEIRA = intent.getStringExtra(FLAG_LABEL.value) ?: null
        val TRANSACAO = intent.getStringExtra(TRANSACTION_LABEL.value) ?: null
        val AUTORIZACAO = intent.getStringExtra(AUTHORIZATION_LABEL.value) ?: null
        val NSU = intent.getStringExtra(NSU_LABEL.value) ?: null
        val ERRO = intent.getStringExtra(ERROR_LABEL.value) ?: null

        return TransactionData(
            SERIAL,
            BANDEIRA,
            TRANSACAO,
            AUTORIZACAO,
            NSU,
            null,
            ERRO
        )
    }
}
