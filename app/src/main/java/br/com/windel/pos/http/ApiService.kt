package br.com.windel.pos.http

import android.os.Build
import android.util.Log
import br.com.windel.pos.BuildConfig
import br.com.windel.pos.data.dtos.DataPaymentResponse
import br.com.windel.pos.enums.EndpointEnum
import br.com.windel.pos.gateways.BasicAuthInterceptor
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class ApiService {
    private var httpClient = OkHttpClient.Builder()
        .addInterceptor(BasicAuthInterceptor(BuildConfig.WINDEL_POS_AUTH_USER, BuildConfig.WINDEL_POS_AUTH_PASS)).build()

    fun findPayment(callback: Callback) {
        val request = Request.Builder()
            .url("${EndpointEnum.GATEWAY_PAGBANK_TERMINAL.value}/${Build.SERIAL}")
            .build()

        try {
            httpClient.newCall(request).enqueue(callback)
        } catch (e: Exception) {
            Log.e(this.javaClass.name, e.message.toString())
        }
    }

    fun findPaymentProcessing(callback: Callback) {
        val request = Request.Builder()
            .url("${EndpointEnum.GATEWAY_PAGBANK_TERMINAL.value}/processing/${Build.SERIAL}")
            .build()

        try {
            httpClient.newCall(request).enqueue(callback)
        } catch (e: Exception) {
            Log.e(this.javaClass.name, e.message.toString())
        }
    }

    fun sendProccessingPayment(orderId: String) {
        try {
            val requestProcessing = Request.Builder()
                .url("${EndpointEnum.GATEWAY_PAGBANK_ORDER.value}/${orderId}/processing")
                .build()

            executeRequest(requestProcessing, {}, {})
        } catch (e: IOException) {
            Log.e(this.javaClass.name, e.message.toString())
        }

    }

    fun sendSuccessPayment(dataPayment: DataPaymentResponse, onSuccess: () -> Unit, onFailed: () -> Unit) {
        try {
            val request = Request.Builder()
                .url("${EndpointEnum.GATEWAY_PAGBANK_ORDER.value}/${dataPayment.data?.orderId}/payed")
                .post(
                    FormBody.Builder()
                        .add("terminalSerial", dataPayment.data?.terminalSerial.orEmpty())
                        .add("flag", dataPayment.data?.flag.orEmpty())
                        .add(
                            "transactionType",
                            dataPayment.data?.transactionType.orEmpty()
                        )
                        .add("authorization", dataPayment.data?.authorization.orEmpty())
                        .add("nsu", dataPayment.data?.nsu.orEmpty())
                        .add("orderId", dataPayment.data?.orderId.orEmpty())
                        .build()
                )
                .build()

            executeRequest(request, onSuccess, onFailed)
        } catch (e: IOException) {
            Log.e(this.javaClass.name, e.message.toString())
        }
    }

     fun sendCanceledPayment(orderId: String, onSuccess: () -> Unit, onFailed: () -> Unit){
        try {
            val request = Request.Builder()
                .url("${EndpointEnum.GATEWAY_PAGBANK_ORDER.value}/${orderId}/canceled")
                .build()

            executeRequest(request, onSuccess, onFailed)
        } catch (e: IOException) {
            Log.e(this.javaClass.name, e.message.toString())
        }
    }

     fun sendFailedPayment(error: String, orderId: String, onSuccess: () -> Unit, onFailed: () -> Unit){
        try {
            val request = Request.Builder()
                .url("${EndpointEnum.GATEWAY_PAGBANK_ORDER.value}/${orderId}/failed")
                .post(
                    FormBody.Builder().add("error", error)
                        .build()
                )
                .build()

            executeRequest(request, onSuccess, onFailed)
        } catch (e: IOException) {
            Log.e(this.javaClass.name, e.message.toString())
        }
    }

    private fun executeRequest(request: Request, onSuccess:  () -> Unit, onFailed: () -> Unit) {
        httpClient.newCall(request).enqueue( object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onFailed()
                e.printStackTrace()
                call.cancel()
            }

            override fun onResponse(call: Call, response: Response) {
                if(response.isSuccessful) {
                    onSuccess()
                } else {
                    throw IOException("Requisição não foi bem sucedida: $response")
                }
                response.close()
            }})
    }
}