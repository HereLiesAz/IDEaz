package com.hereliesaz.ideaz.models

data class LogMessage(
    val timestamp: Long = System.currentTimeMillis(),
    val sender: String, // "User" or "AI"
    val message: String
)