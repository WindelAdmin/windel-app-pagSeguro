package br.com.windel.pos

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPag
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagActivationData
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagEventData
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagEventListener
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagInitializationResult
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagPaymentData
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagTransactionResult
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagVoidData
import br.com.windel.pos.data.dtos.DataPayment
import br.com.windel.pos.data.dtos.DataPaymentResponse
import br.com.windel.pos.data.dtos.TransactionResultData
import br.com.windel.pos.data.entities.PaymentEntity
import br.com.windel.pos.database.AppDatabase
import br.com.windel.pos.enums.ErrorEnum
import br.com.windel.pos.enums.EventsEnum
import br.com.windel.pos.http.ApiService
import com.airbnb.lottie.LottieAnimationView
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okio.IOException
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {
    private lateinit var buttonExit: ImageButton
    private lateinit var buttonReprint: ImageButton
    private lateinit var buttonReversal: ImageButton
    private lateinit var buttonSettings: ImageButton
    private lateinit var buttonFindPayment: LottieAnimationView
    private lateinit var buttonCancel: Button
    private lateinit var lblStatus: TextView
    private lateinit var currentOrderId: String
    var transactionResultCode = -1

    private lateinit var context: Context

    private val paymentService = ApiService()
    private lateinit var plugPag: PlugPag

    companion object {
        var SERIAL: String = ""
        var CODIGO_ATIVACAO = ""

        fun getSerial(): String {
            return SERIAL
        }

        fun setSerial(serial: String) {
            SERIAL = serial
        }

        fun getCodigoAtivacao(): String {
            return CODIGO_ATIVACAO
        }

        fun setCodigoAtivacao(codigoAtivacao: String) {
            CODIGO_ATIVACAO = codigoAtivacao
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        supportActionBar?.hide()
        window.setFlags(
            FLAG_FULLSCREEN,
            FLAG_FULLSCREEN
        )

        context = this

        plugPag = PlugPag(context)
        setSharedPref()

        currentOrderId = ""
        lblStatus = findViewById(R.id.lblStatus);
        lblStatus.text = ""
        buttonSettings = findViewById(R.id.buttonSettings)
        buttonExit = findViewById(R.id.buttonExit);
        buttonReprint = findViewById(R.id.buttonReprint)
        buttonReversal = findViewById(R.id.buttonReversal)
        buttonCancel = findViewById(R.id.buttonCloseDialog)
        buttonFindPayment = findViewById(R.id.lottieAnimationView)
        buttonFindPayment.setAnimation(R.raw.sync_idle)
        buttonFindPayment.playAnimation()

        buttonCancel.isEnabled = false
        buttonCancel.isVisible = false

        runOnUiThread {
            lblStatus.text = ""
            buttonFindPayment.setAnimation(R.raw.sync_idle)
            buttonFindPayment.playAnimation()
            buttonFindPayment.layoutParams.width = 500
            buttonFindPayment.layoutParams.height = 500
        }

        buttonExit.setOnClickListener {
            finish()
        }

        buttonReprint.setOnClickListener {
            reprint()
        }

        buttonReversal.setOnClickListener {
            showDialogReversal()
        }

        buttonSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        buttonFindPayment.setOnClickListener {
            runOnUiThread {
                buttonFindPayment.setAnimation(R.raw.sync_loading)
                buttonFindPayment.playAnimation()
                lblStatus.text = ""
            }

            CoroutineScope(Dispatchers.Main).launch {
                withContext(Dispatchers.IO) {
                    checkPayments()
                }
            }
        }

        buttonCancel.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                withContext(Dispatchers.IO) {
                    plugPag.abort()
                }
            }
        }

        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.IO) {
                assertPayment()
            }
        }

        checkProcessingPayment()

    }

    override fun onResume() {
        super.onResume()
        setSharedPref()
    }

    @SuppressLint("HardwareIds")
    private fun setSharedPref() {
        val sharedPreferences = getSharedPreferences("windelConfig", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        val terminalNumberCached = sharedPreferences.getString("terminalSerialNumber", "")?.trim()
        val terminalNumber = Build.SERIAL ?: terminalNumberCached


        val builder = AlertDialog.Builder(context)
        builder.setTitle("Configuração")
        builder.setPositiveButton("Ir") { dialog, which ->
            dialog.dismiss()
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        builder.setNegativeButton("Sair") { dialog, which ->
            finish()
        }

        if (!terminalNumber.equals("")) {
            sharedPreferences.getString("terminalSerialNumber", "")?.let { setSerial(it) }

            if(terminalNumberCached.equals("")) {
                editor.putString("terminalSerialNumber", terminalNumber)
                editor.apply()
            }
        } else {
            runOnUiThread {
                builder.setTitle("Configuração")
                builder.setMessage("Configure o SERIAL do seu terminal")
                builder.show()
            }
        }
    }

    private fun activatePlugPag(): PlugPagInitializationResult {
        return plugPag.initializeAndActivatePinpad(PlugPagActivationData(
            getCodigoAtivacao()
        ));
    }

    private fun checkPayments() {
        buttonFindPayment.isClickable = false
        if (checkInternetConnection()) {
            paymentService.findPayment(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    setLottieAnimation(R.raw.sync_idle, 500, 500, false)
                    e.printStackTrace()
                    call.cancel()
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) throw IOException("Requisição mal sucedida: $response")

                    try {
                        if (response.body.contentLength() == 0L) {
                            response.close()
                            call.cancel()
                            openDialogPaymentNotFound()
                            setLottieAnimation(R.raw.sync_idle, 500, 500, false)
                            buttonFindPayment.isClickable = true
                            return
                        }

                        val data = Gson().fromJson(response.body.string(), DataPayment::class.java)

                        response.close()
                        call.cancel()

                        currentOrderId = data.orderId

                        paymentService.sendProccessingPayment(data.orderId)

                        pay(data)

                    } catch (e: Exception) {
                        Log.e(this.javaClass.name, e.message.toString())
                    }
                }
            })
        } else {
            runOnUiThread {
                lblStatus.text = ErrorEnum.CONNECTION_ERROR.value
                lblStatus.setTextColor(Color.parseColor("#a31a1a"))
                setLottieAnimation(R.raw.sync_idle, 500, 500, true)
            }
        }
    }

    private fun openDialogPaymentNotFound() {
        runOnUiThread {
            val builder = AlertDialog.Builder(context)
            builder.setCancelable(false)
            builder.setTitle("Ops!")
            builder.setMessage("Nenhum pedido de pagamento encontrado.")
            builder.setPositiveButton("Ok") { dialog, which ->
                dialog.dismiss()
            }

            builder.show()
        }
    }

    private fun listenEventsPlugPag(){
        plugPag.setEventListener(object : PlugPagEventListener {
            override fun onEvent(data: PlugPagEventData) {
                if(data.eventCode >= 0) {

                        data.customMessage?.let {
                            lblStatus.text = it
                        }

                }

                if(data.eventCode == 18) {
                    transactionResultCode = data.eventCode
                    setLottieAnimation(R.raw.success, 200, 200, false)
                    return
                }

                if(data.eventCode == 0) {
                    setLottieAnimation(R.raw.contactless, 500, 500, true)
                    buttonCancel.isEnabled = true
                    buttonCancel.isVisible = true
                    return
                }

                if(data.eventCode == 5) {
                    if(transactionResultCode == 19) return
                    setLottieAnimation(R.raw.loader, 400, 400, true)
                    return
                }

                buttonCancel.isEnabled = false
                buttonCancel.isVisible = false
            }
        })
    }

    private fun pay(paymentData: DataPayment) {
        try {
            var typePayment: Int = PlugPag.TYPE_DEBITO
            var installmentsType: Int = PlugPag.INSTALLMENT_TYPE_A_VISTA

            when(paymentData.transactionType) {
                "DEBITO" -> {
                    typePayment = PlugPag.TYPE_DEBITO
                }
                "CREDITO" -> {
                    typePayment = PlugPag.TYPE_CREDITO
                }
                "PARCELADO_EMISSOR" -> {
                    typePayment = PlugPag.INSTALLMENT_TYPE_PARC_COMPRADOR
                    installmentsType = PlugPag.INSTALLMENT_TYPE_PARC_COMPRADOR
                }
                "PARCELADO_LOJISTA" -> {
                    typePayment = PlugPag.INSTALLMENT_TYPE_PARC_VENDEDOR
                    installmentsType = PlugPag.INSTALLMENT_TYPE_PARC_VENDEDOR
                }
                "VOUCHER" -> {
                    typePayment = PlugPag.TYPE_VOUCHER
                }
                "PIX" -> {
                    typePayment = PlugPag.TYPE_PIX
                }
            }

            val paymentDataPlugPag = PlugPagPaymentData(
                typePayment,
                paymentData.transactionValue.toInt(),
                installmentsType,
                paymentData.installments ?: 1,
                getSerial().substring(0, 9)
            );

            if (activatePlugPag().result == PlugPag.RET_OK) {
                listenEventsPlugPag()
                val result = plugPag.doPayment(paymentDataPlugPag);
                resolveTransactionResult(result)
            }
        } catch (e: Exception) {
            showToast(e.message ?: "")
        }
    }

    private fun reversal(nsu: String) {
        runOnUiThread {
            lblStatus.text = "PROCESSANDO"
            setLottieAnimation(R.raw.loader, 400, 400, false)
        }

        if (checkInternetConnection()) {
            paymentService.findPaymentByNsu(nsu, object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        lblStatus.text = ""
                        setLottieAnimation(R.raw.sync_idle, 500, 500, false)
                        showToast(e.message.toString())
                    }
                    call.cancel()
                }

                override fun onResponse(call: Call, response: Response) {
                    runOnUiThread {
                        lblStatus.text = ""
                        setLottieAnimation(R.raw.loader, 400, 400, false)
                    }

                    if (!response.isSuccessful) throw IOException("Requisição mal sucedida: $response")

                    try {
                        if (response.body.contentLength() == 0L) {
                            runOnUiThread {
                                lblStatus.text = ""
                                setLottieAnimation(R.raw.sync_idle, 500, 500, false)
                            }
                            response.close()
                            call.cancel()
                            buttonFindPayment.isClickable = true
                            return
                        }

                        val data = Gson().fromJson(response.body.string(), DataPayment::class.java)

                        response.close()
                        call.cancel()

                        val initResult = plugPag.initializeAndActivatePinpad(
                            PlugPagActivationData(
                                getCodigoAtivacao()
                            )
                        );

                        if (initResult.result == PlugPag.RET_OK) {
                            CoroutineScope(Dispatchers.Main).launch {
                                withContext(Dispatchers.IO) {
                                    listenEventsPlugPag()
                                    val result = plugPag.voidPayment(
                                        PlugPagVoidData(
                                            data.transactionCode ?: "",
                                            data.transactionIdInTerminal ?: "",
                                            true
                                        )
                                    )
                                    resolveTransactionResult(result)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        showToast(e.message.toString())
                    }
                }
            })
        } else {
            runOnUiThread {
                lblStatus.text = ErrorEnum.CONNECTION_ERROR.value
                lblStatus.setTextColor(Color.parseColor("#a31a1a"))
                setLottieAnimation(R.raw.sync_idle, 500, 500, true)
            }
        }
    }

    private fun resolveTransactionResult(result: PlugPagTransactionResult) {
        if(result.errorCode == "0000") {
            if(transactionResultCode == -1) {
                runBlocking {
                    lblStatus.text = result.message
                    setLottieAnimation(R.raw.failed, 250, 250, false)
                    delay(5000L)
                }
                return
            }

            runOnUiThread {
                lblStatus.text = ""
                setLottieAnimation(R.raw.sync_idle, 500, 500, true)
            }
        }
        else {
            runOnUiThread {
                lblStatus.text = result.message
                setLottieAnimation(R.raw.failed, 250, 250, false)
            }

            Thread.sleep(3500)

            runOnUiThread {
                lblStatus.text = ""
                setLottieAnimation(R.raw.sync_idle, 500, 500, true)
            }
        }

        resolvePaymentResult(result)
    }

    private fun resolvePaymentResult(plugPagTransactionResult: PlugPagTransactionResult) {
        var status = EventsEnum.EVENT_SUCCESS
        var error = ""

        if (plugPagTransactionResult.errorCode != "0000") {
            if (plugPagTransactionResult.errorCode == "A001"
                || plugPagTransactionResult.errorCode == "A202"
                || plugPagTransactionResult.errorCode == "A242"
                || plugPagTransactionResult.errorCode == "C013"
            ) {
                status = EventsEnum.EVENT_CANCELED
            } else {
                status = EventsEnum.EVENT_FAILED
                error = plugPagTransactionResult.message ?: ""
            }
        }

        var paymentType = ""

        when (plugPagTransactionResult.paymentType) {
            PlugPag.TYPE_CREDITO -> {
                if (plugPagTransactionResult.installments != null) {
                    paymentType = "CREDITO_PARCELADO"
                } else {
                    paymentType = "CREDITO"
                }
            }
            PlugPag.TYPE_DEBITO -> paymentType = "DEBITO"
            PlugPag.TYPE_PIX -> paymentType = "PIX"
            PlugPag.TYPE_VOUCHER -> paymentType = "VOUCHER"
        }

        val plugPagToDataResponse = TransactionResultData(
            getSerial(),
            plugPagTransactionResult.cardBrand,
            paymentType,
            plugPagTransactionResult.nsu,
            plugPagTransactionResult.nsu,
            currentOrderId,
            plugPagTransactionResult.transactionCode,
            plugPagTransactionResult.transactionId,
            error
        )

        val data = DataPaymentResponse(
            status.value, plugPagToDataResponse
        )

        if (checkInternetConnection()) {
            if (data.status === EventsEnum.EVENT_SUCCESS.value) {
                paymentService.sendSuccessPayment(data, {}, {})

            }   else if (data.status === EventsEnum.EVENT_CANCELED.value)
                paymentService.sendCanceledPayment(currentOrderId, {
                }, {
                    showServerError()
                })
            else
                paymentService.sendFailedPayment(
                    data.data?.error.orEmpty(),
                    currentOrderId,
                    {}, {}
                )
        } else {
            saveOnDatabase(data)
        }

        buttonFindPayment.isClickable = true
    }

    private fun reprint() {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_reprint, null)

        val buttonCancel = view.findViewById<ImageButton>(R.id.buttonCloseDialog)
        val buttonOption1 = view.findViewById<Button>(R.id.buttonOption1)
        val buttonOption2 = view.findViewById<Button>(R.id.buttonOption2)

        val builder = AlertDialog.Builder(context)
        builder.setCancelable(false)
        builder.setView(view)

        try {
            val dialog = builder.create()
            dialog.show()

            buttonCancel.setOnClickListener {
                dialog.dismiss()
            }

            buttonOption1.setOnClickListener {
                dialog.dismiss()
                val dialogLoading = showDialogLoading("imprimindo...")
                CoroutineScope(Dispatchers.Main).launch {
                    withContext(Dispatchers.IO) {
                        reprintCustomerCoupon()
                        dialogLoading.dismiss()
                    }
                }
            }

            buttonOption2.setOnClickListener {
                dialog.dismiss()
                val dialogLoading = showDialogLoading("imprimindo...")
                CoroutineScope(Dispatchers.Main).launch {
                    withContext(Dispatchers.IO) {
                        reprintEstablishmentCoupon()
                        dialogLoading.dismiss()
                    }
                }
            }
        }catch(e: Exception) {
            e.printStackTrace()
        }
    }

    private fun reprintCustomerCoupon() {
        if(activatePlugPag().result == 0) {
            plugPag.reprintCustomerReceipt()
        }
    }

    private fun reprintEstablishmentCoupon() {
        if(activatePlugPag().result == 0) {
            plugPag.reprintStablishmentReceipt()
        }
    }

    private fun checkProcessingPayment() {
        if(checkInternetConnection()) {
            paymentService.findPaymentProcessing(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Toast.makeText(context, e.message.toString(), Toast.LENGTH_LONG).show()
                    call.cancel()
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        if (response.body.contentLength() == 0L) {
                            response.close()
                            call.cancel()
                            return
                        }

                        val data = Gson().fromJson(response.body.string(), DataPayment::class.java)

                        currentOrderId = data.orderId

                        openDialogPaymentProcessing(data)
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message.toString(), Toast.LENGTH_LONG).show()
                    }
                }
            })
        } else {
            showConnectionError()
        }
    }

    private suspend fun assertPayment() {
        try {
            val db = Room.databaseBuilder(
                applicationContext,
                AppDatabase::class.java, "payments-backup"
            ).build()

            val paymentsPendingToReturn = db.paymentDao().getAll()

            if (paymentsPendingToReturn.isNotEmpty()) {

                if(checkInternetConnection()) {
                    paymentsPendingToReturn.forEach {

                        val data = DataPaymentResponse(
                            it.status,
                            TransactionResultData(
                                it.terminalSerial,
                                it.flag,
                                it.transactionType,
                                it.authorization,
                                it.nsu,
                                it.orderId,
                                it.error
                            )
                        )

                        if (data.status === EventsEnum.EVENT_SUCCESS.value)
                            paymentService.sendSuccessPayment(data, {
                                setLottieAnimation(R.raw.sync_idle, 500, 500, false)
                            }, {
                                showServerError()
                            })
                        else if (data.status === EventsEnum.EVENT_CANCELED.value)
                            paymentService.sendCanceledPayment(
                                currentOrderId,
                                {
                                    setLottieAnimation(R.raw.sync_idle, 500, 500, false)
                                },
                                {
                                    showServerError()
                                })
                        else
                            paymentService.sendFailedPayment(
                                data.data?.error.orEmpty(),
                                currentOrderId,
                                {
                                    setLottieAnimation(R.raw.sync_idle, 500, 500, false)
                                },
                                {
                                    showServerError()
                                })

                        db.paymentDao().delete(it)
                    }
                } else {
                    showConnectionError()
                }
            }
        } catch (e: IOException) {
            showToast(e.message ?: "")
        }
    }

    private fun saveOnDatabase(data: DataPaymentResponse) {

        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "app"
        ).build()

        val paymentModel = PaymentEntity(
            0,
            data.status,
            data.data?.terminalSerial,
            data.data?.flag,
            data.data?.transactionType,
            data.data?.authorization,
            data.data?.nsu,
            currentOrderId,
            data.data?.error
        )

        lifecycleScope.launch {
            try {
                db.paymentDao().insert(paymentModel)
                setLottieAnimation(R.raw.sync_idle, 500, 500, false)
            } catch (e: Exception) {
                runOnUiThread {
                    setLottieAnimation(R.raw.sync_idle, 500, 500, false)
                    lblStatus.text = e.message.toString()
                    Log.e(this.javaClass.name, e.message.toString())
                }
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    fun checkInternetConnection(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            connectivityManager.activeNetwork ?: return false
        } else {
            TODO("VERSION.SDK_INT < M")
        }
        val networkCapabilities =
            connectivityManager.getNetworkCapabilities(network) ?: return false

        return when {
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                true
            }

            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                return try {
                    val url = URL("https://www.google.com")
                    val urlConnection = url.openConnection() as HttpURLConnection
                    urlConnection.connectTimeout = 1000
                    urlConnection.connect()
                    urlConnection.responseCode == 200
                } catch (e: Exception) {
                    false
                }
            }

            else -> false
        }
    }

    private fun setLottieAnimation(animation: Int, width: Int, height: Int, isLooping: Boolean) {
        buttonFindPayment.setAnimation(animation)
        buttonFindPayment.layoutParams.width = width
        buttonFindPayment.layoutParams.height = height
        buttonFindPayment.loop(isLooping)
        buttonFindPayment.playAnimation()
    }

    private fun openDialogPaymentProcessing(data: DataPayment) {
        runOnUiThread {
            val builder = AlertDialog.Builder(context)
            builder.setCancelable(false)
            builder.setTitle("Ops!")
            builder.setMessage("Você possui um pagamento em aberto, o que deseja fazer?")
            builder.setPositiveButton("Refazer Pagamento") { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    withContext(Dispatchers.IO) {
                            pay(data)
                        }
                    }
            }

            builder.setNegativeButton("Cancelar") { dialog, _ ->
                dialog.cancel()

                val progressDialog = ProgressDialog(context)
                progressDialog.setMessage("Cancelando pagamento..")
                progressDialog.setCancelable(false)
                progressDialog.show()

                try {
                    runBlocking {
                            paymentService.sendCanceledPayment(
                                data.orderId,
                                {
                                    progressDialog.dismiss()
                                }, {
                                    progressDialog.dismiss()
                                    showServerError()
                                })

                    }
                } catch (e: Exception) {
                    progressDialog.dismiss()
                    showToast(e.message ?: "")
                }
            }

            val alertDialog: AlertDialog = builder.create()
            alertDialog.show()
        }
    }

    private fun showDialogReversal() {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_reversal, null)

        val editTextNsu = view.findViewById<EditText>(R.id.editTextNsu)
        val buttonOption1 = view.findViewById<Button>(R.id.buttonOption1)
        val buttonOption2 = view.findViewById<Button>(R.id.buttonOption2)

        val builder = AlertDialog.Builder(context)
        builder.setCancelable(false)
        builder.setView(view)

        try {
            val dialog = builder.create()
            dialog.show()

            buttonOption1.setOnClickListener {
                dialog.dismiss()
            }

            buttonOption2.setOnClickListener {
                val nsu = editTextNsu.text
                if(nsu.isNotBlank()){
                    CoroutineScope(Dispatchers.Main).launch {
                        withContext(Dispatchers.IO) {
                            reversal(nsu.toString())
                        }
                    }
                    dialog.dismiss()
                } else {
                    showToast("Preencha o NSU para continuar.")
                    editTextNsu.requestFocus()
                }

            }
        }catch(e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showServerError() {
        runOnUiThread {
            lblStatus.text = ErrorEnum.SERVER_ERROR.value
            lblStatus.setTextColor(Color.parseColor("#a31a1a"))
        }
    }

    private fun showConnectionError() {
        runOnUiThread {
            lblStatus.text = ErrorEnum.CONNECTION_ERROR.value
            lblStatus.setTextColor(Color.parseColor("#a31a1a"))
        }
    }

    private fun showToast(msg: String) {
        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun showDialogLoading(text: String): ProgressDialog {
        val progressDialog = ProgressDialog(context)
        progressDialog.setMessage(text)
        progressDialog.setCancelable(false)
        progressDialog.show()

        return progressDialog
    }
}