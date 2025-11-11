package com.hereliesaz.ideaz.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

object ApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor) // Add the Auth Interceptor
        .build()

    private fun <T> create(baseUrl: String, service: Class<T>): T {
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        return retrofit.create(service)
    }

    val julesApiService: JulesApiService = create(
        "https://jules.googleapis.com/",
        JulesApiService::class.java
    )
}