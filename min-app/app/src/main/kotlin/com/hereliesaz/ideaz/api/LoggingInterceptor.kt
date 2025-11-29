package com.hereliesaz.ideaz.api

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

object LoggingInterceptor : Interceptor {
    private const val TAG = "API_LOG"

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        Log.d(TAG, "Sending request: ${request.method} ${request.url}")

        val response = chain.proceed(request)
        Log.d(TAG, "Received response: ${response.code} for ${request.url}")
        return response
    }
}
