package com.neuropocket.app.engine

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Общий HTTP-клиент приложения (API GitHub, каталоги). */
object NetHttp {
    val client: OkHttpClient = clientFor(60)

    private val cache = mutableMapOf<Int, OkHttpClient>()

    @Synchronized
    fun clientFor(readSec: Int): OkHttpClient {
        val s = readSec.coerceIn(15, 600)
        return cache.getOrPut(s) {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(s.toLong(), TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }
}
