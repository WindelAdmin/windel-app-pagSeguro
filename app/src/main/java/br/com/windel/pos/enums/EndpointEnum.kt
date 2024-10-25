package br.com.windel.pos.enums
import br.com.windel.pos.BuildConfig

enum class EndpointEnum(val value: String){
    GATEWAY_PAGBANK_ORDER("${BuildConfig.WINDEL_POS_HOST}/gateway-pagbank/order"),
    GATEWAY_PAGBANK_TERMINAL("${BuildConfig.WINDEL_POS_HOST}/gateway-pagbank/terminal")
}