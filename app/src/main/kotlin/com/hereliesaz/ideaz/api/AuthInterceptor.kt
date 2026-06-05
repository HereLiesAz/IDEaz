package com.hereliesaz.ideaz.api

import okhttp3.Interceptor
import okhttp3.Response

object AuthInterceptor : Interceptor {

    @Volatile
    var apiKey: String? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val builder = originalRequest.newBuilder()

        // Only attach the header when we actually have a key. A blank key would
        // produce "X-Goog-Api-Key: " and a confusing 400 instead of a clear 401.
        apiKey?.takeIf { it.isNotBlank() }?.let {
            builder.header("X-Goog-Api-Key", it)
        }

        val newRequest = builder.build()
        return chain.proceed(newRequest)
    }
}