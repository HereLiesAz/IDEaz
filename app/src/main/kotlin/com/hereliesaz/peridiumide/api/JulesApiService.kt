package com.hereliesaz.peridiumide.api

import retrofit2.http.GET

interface JulesApiService {
    @GET("placeholder")
    suspend fun getPlaceholder(): String
}
