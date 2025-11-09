package com.hereliesaz.ideaz.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MainViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ViewModelProvider.AndroidViewModelFactory.getInstance(application).create(modelClass) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}