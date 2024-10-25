package br.com.windel.pos.enums

enum class ErrorEnum(val value: String) {
    SERVER_ERROR("Não foi possível conectar-se ao servidor, entre em contato com o suporte."),
    CONNECTION_ERROR("Sem conexão com a rede, verifique sua conexão Wi-fi ou Dados Móveis."),
}