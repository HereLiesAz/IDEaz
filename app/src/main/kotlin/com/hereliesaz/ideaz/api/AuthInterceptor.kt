package com.hereliesaz.ideaz.api

import okhttp3.Interceptor
import okhttp3.Response

object AuthInterceptor : Interceptor {

    @Volatile
    var apiKey: String? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val builder = originalRequest.newBuilder()

        apiKey?.let {
            builder.header("x-goog-api-key", it)
        }

        val newRequest = builder.build()
        return chain.proceed(newRequest)
    }
}