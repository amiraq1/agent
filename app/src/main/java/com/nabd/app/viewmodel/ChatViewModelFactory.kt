package com.nabd.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nabd.app.data.MemoryManager
import com.nabd.app.data.SettingsManager
import com.nabd.app.data.local.ChatDao

class ChatViewModelFactory(
    private val application: Application,
    private val settingsManager: SettingsManager,
    private val chatDao: ChatDao,
    private val memoryManager: MemoryManager,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(application, settingsManager, chatDao, memoryManager, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
