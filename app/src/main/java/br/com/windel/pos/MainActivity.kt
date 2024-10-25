package br.com.windel.pos

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPag
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagActivationData
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagEventData
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagEventListener
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagPaymentData
import br.com.uol.pagseguro.plugpagservice.wrapper.PlugPagTransactionResult
import br.com.windel.pos.data.dtos.DataPayment
import br.com.windel.pos.data.dtos.DataPaymentResponse
import br.com.windel.pos.data.dtos.TransactionData
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
    private lateinit var buttonSettings: ImageButton
    private lateinit var buttonCancel: Button
    private lateinit var lblStatus: TextView
    private lateinit var currentOrderId: String
    private lateinit var lottieAnimationView: LottieAnimationView
    private lateinit var context: Context
    private lateinit var plugpag: PlugPag

    private val paymentService = ApiService()

    companion object {
        var SERIAL: String = ""

        fun getSerial(): String {
            return SERIAL
        }

        fun setSerial(serial: String) {
            SERIAL = serial
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
        plugpag = PlugPag(context);

        setSharedPref()

        currentOrderId = ""
        lblStatus = findViewById(R.id.lblStatus);
        lblStatus.text = ""
        buttonSettings = findViewById(R.id.buttonSettings)
        buttonExit = findViewById(R.id.buttonExit);
        buttonCancel = findViewById(R.id.buttonCancel)
        lottieAnimationView = findViewById(R.id.lottieAnimationView)
        lottieAnimationView.setAnimation(R.raw.sync_idle)
        lottieAnimationView.playAnimation()

        buttonCancel.isEnabled = false
        buttonCancel.isVisible = false

        val layoutParams = lottieAnimationView.layoutParams

        buttonSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        runOnUiThread {
            lblStatus.text = ""
            lottieAnimationView.setAnimation(R.raw.sync_idle)
            lottieAnimationView.playAnimation()
            layoutParams.width = 500
            layoutParams.height = 500
            lottieAnimationView.layoutParams = layoutParams
        }

        lottieAnimationView.setOnClickListener {
            runOnUiThread {
                lottieAnimationView.setAnimation(R.raw.sync_loading)
                lottieAnimationView.playAnimation()
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
                    plugpag.abort()
                }
            }
        }

        buttonExit.setOnClickListener {
            finish()
        }

        checkProccessingPayment()

        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.IO) {
                assertPayment()
            }
        }

    }

    override fun onResume() {
        super.onResume()
        setSharedPref()
    }

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

    private fun checkPayments() {
        lottieAnimationView.isClickable = false
        if (checkInternetConnection(this)) {
            paymentService.findPayment(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    setLottieToSyncIdle()
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
                            setLottieToSyncIdle()
                            lottieAnimationView.isClickable = true
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

            var paymentDataPlugPag = PlugPagPaymentData(
                typePayment,
                paymentData.transactionValue.toInt(),
                installmentsType,
                paymentData.installments ?: 1,
                "11ada11");


            var initResult = plugpag.initializeAndActivatePinpad(PlugPagActivationData("749879"));

            if (initResult.result == PlugPag.RET_OK) {

                var transactionResultCode = -1

                plugpag.setEventListener(object : PlugPagEventListener {
                    override fun onEvent(data: PlugPagEventData) {
                        if(data.eventCode >= 0) {
                            runBlocking {
                                data.customMessage?.let {
                                    lblStatus.text = it
                                }
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

                var result = plugpag.doPayment(paymentDataPlugPag);

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

                resolveTransactionResult(result)


            }
        } catch (e: Exception) {
            showToast(e.message ?: "")
        }
    }

    private fun resolveTransactionResult(plugPagTransactionResult: PlugPagTransactionResult) {
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

        val plugPagToDataResponse = TransactionData(
            getSerial(),
            plugPagTransactionResult.cardBrand,
            paymentType,
            plugPagTransactionResult.hostNsu,
            plugPagTransactionResult.transactionId,
            error
        )

        val data = DataPaymentResponse(
            status.value, plugPagToDataResponse
        )

        if (checkInternetConnection(this)) {
            data.data?.terminalSerial = getSerial()
            data.data?.orderId = currentOrderId

            if (data.status === EventsEnum.EVENT_SUCCESS.value) {
                paymentService.sendSuccessPayment(data, {}, {})

            }   else if (data.status === EventsEnum.EVENT_CANCELED.value)
                paymentService.sendCanceledPayment(currentOrderId, {
                }, {
                    setLottieServerError()
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

        lottieAnimationView.isClickable = true
    }
    private fun checkProccessingPayment() {
        paymentService.findPaymentProcessing(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
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
                    Log.e(this.javaClass.name, e.message.toString())
                }
            }
        })
    }

    //STORAGE LOCAL
    private suspend fun assertPayment() {
        try {
            val db = Room.databaseBuilder(
                applicationContext,
                AppDatabase::class.java, "payments-backup"
            ).build()
            val paymentsPendingToReturn = db.paymentDao().getAll()

            if (paymentsPendingToReturn.isNotEmpty()) {
                paymentsPendingToReturn.forEach {
                    it

                    val data = DataPaymentResponse(
                        it.status,
                        TransactionData(
                            it?.terminalSerial,
                            it?.flag,
                            it?.transactionType,
                            it?.authorization,
                            it?.nsu,
                            it?.orderId,
                            it?.error
                        )
                    )

                    if (data.status === EventsEnum.EVENT_SUCCESS.value)
                        paymentService.sendSuccessPayment(data, { setLottieToSyncIdle() }, {
                            setLottieErrorRequest()
                        })
                    else if (data.status === EventsEnum.EVENT_CANCELED.value)
                        paymentService.sendCanceledPayment(
                            currentOrderId,
                            { setLottieToSyncIdle()},
                            {
                                setLottieErrorRequest()
                            })
                    else
                        paymentService.sendFailedPayment(
                            data.data?.error.orEmpty(),
                            currentOrderId,
                            { setLottieToSyncIdle()},
                            {
                                setLottieErrorRequest()
                            })

                    db.paymentDao().delete(it)
                }
            }
        } catch (e: IOException) {
            runOnUiThread {
                lblStatus.text = e.message.toString()
            }
            Log.e(this.javaClass.name, e.message.toString())
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
                setLottieToSyncIdle()
            } catch (e: Exception) {
                runOnUiThread {
                    setLottieToSyncIdle()
                    lblStatus.text = e.message.toString()
                    Log.e(this.javaClass.name, e.message.toString())
                }
            }
        }
    }

    fun checkInternetConnection(context: Context): Boolean {
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
                true // Está conectado ao Wi-Fi, presumimos que há internet
            }

            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                // Está conectado aos dados móveis, verificamos se há internet
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

    private fun openDialogPaymentProcessing(data: DataPayment) {
        runOnUiThread {
            val builder = AlertDialog.Builder(context)
            builder.setCancelable(false)
            builder.setTitle("Ops!")
            builder.setMessage("Você possui um pagamento em aberto, o que deseja fazer?")
            builder.setPositiveButton("Refazer Pagamento") { dialog, which ->
                CoroutineScope(Dispatchers.Main).launch {
                    withContext(Dispatchers.IO) {
                            pay(data)
                        }
                    }
            }

            builder.setNegativeButton("Cancelar") { dialog, which ->
                dialog.cancel()

                val progressDialog = ProgressDialog(context)
                progressDialog?.setMessage("Cancelando pagamento..")
                progressDialog?.setCancelable(false)
                progressDialog?.show()

                try {
                    runBlocking {
                            paymentService.sendCanceledPayment(
                                data.orderId,
                                {
                                    progressDialog.dismiss()
                                }, {
                                    progressDialog.dismiss()
                                    setLottieErrorRequest()
                                })

                    }
                } catch (e: Exception) {
                    progressDialog.dismiss()
                    Log.e(this.javaClass.name, e.message.toString())
                }
            }

            val alertDialog: AlertDialog = builder.create()
            alertDialog.show()
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

    private fun setLottieToSyncIdle() {
        runOnUiThread {
            val layoutParams = lottieAnimationView.layoutParams
            lottieAnimationView.setAnimation(R.raw.sync_idle)
            lottieAnimationView.playAnimation()
            layoutParams.width = 500
            layoutParams.height = 500
            lottieAnimationView.layoutParams = layoutParams
        }
    }

    private fun setLottieServerError() {
        runOnUiThread {
            lblStatus.text = ErrorEnum.SERVER_ERROR.value
            lblStatus.setTextColor(Color.parseColor("#a31a1a"))
        }
    }

    private fun setLottieErrorRequest() {
        runOnUiThread {
            lblStatus.text = "Houve algum erro na requisição."
            lblStatus.setTextColor(Color.parseColor("#a31a1a"))
        }
    }

    private fun setLottieAnimation(animation: Int, width: Int, height: Int, isLooping: Boolean) {
            lottieAnimationView.setAnimation(animation)
            lottieAnimationView.layoutParams.width = width
            lottieAnimationView.layoutParams.height = height
            lottieAnimationView.loop(isLooping)
            lottieAnimationView.playAnimation()
    }

    private fun showAlertDialog(title: String, msg: String, actionButton: Function<Unit>) {
        runOnUiThread {
            val builder = AlertDialog.Builder(context)
            builder.setCancelable(false)
            builder.setTitle(title)
            builder.setMessage(msg)
            builder.setPositiveButton("Ok") { dialog, which ->
                dialog.dismiss()
                actionButton.apply {}
            }

            builder.show()
        }
    }

    private fun showToast(msg: String) {
        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }
}