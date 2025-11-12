package com.hereliesaz.ideaz.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

object ApiClient {

    // Reverted to the correct Base URL as per your documentation link.
    // The 404 is an auth or project configuration issue, not a host issue.
    private const val BASE_URL = "https://jules.googleapis.com/"

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor) // Add the Auth Interceptor
        .build()

    private val retrofit = Retrofit.Builder()

        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val julesApiService: JulesApiService = retrofit.create(JulesApiService::class.java)
}