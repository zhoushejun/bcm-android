package com.bcm.messenger.wallet.btc.jsonrpc

class RpcError @JvmOverloads internal constructor(
        val code: Int,
        val message: String,
        val data: Any? = null
) {
    override fun toString(): String {
        return "RpcError(code=$code, message='$message', data=$data)"
    }
}