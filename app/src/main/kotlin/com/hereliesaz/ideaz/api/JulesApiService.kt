package com.hereliesaz.ideaz.api

import retrofit2.http.GET

interface JulesApiService {
    @GET("placeholder")
    suspend fun getPlaceholder(): String
}
