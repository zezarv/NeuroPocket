package com.neuropocket.app.engine

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Общий HTTP-клиент приложения (API GitHub, каталоги). */
object NetHttp {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
}
