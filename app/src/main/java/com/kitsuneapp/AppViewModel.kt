package com.kitsuneapp

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsuneapp.data.OwnershipException
import com.kitsuneapp.data.Records
import com.kitsuneapp.data.Repository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppState(val records: Records? = null, val openingError: String? = null, val needsNewDatabase: Boolean = false)

class AppViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val app = application as KitsuneApplication
    private val mutable = MutableStateFlow(AppState())
    val state = mutable.asStateFlow()
    private val working = MutableStateFlow(false)
    val busy = working.asStateFlow()
    private val message = MutableStateFlow<String?>(null)
    val error = message.asStateFlow()
    private var observe: Job? = null
    private lateinit var repository: Repository

    init { open(false) }

    fun open(startNew: Boolean) {
        observe?.cancel()
        mutable.value = AppState()
        observe = viewModelScope.launch {
            try {
                repository = app.repository(startNew)
                repository.observe().collect { mutable.value = AppState(records = it) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutable.value = AppState(openingError = error.userMessage("无法打开本地数据库"),
                    needsNewDatabase = error is OwnershipException)
            }
        }
    }

    fun clearError() { message.value = null }

    fun save(onSuccess: () -> Unit = {}, work: suspend Repository.() -> Unit) {
        if (working.value || !::repository.isInitialized) return
        working.value = true
        clearError()
        viewModelScope.launch {
            try {
                repository.work()
                working.value = false
                onSuccess()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                message.value = error.userMessage("操作失败，请重试")
            } finally {
                working.value = false
            }
        }
    }
}

fun Exception.userMessage(fallback: String): String = when (this) {
    is IllegalArgumentException, is IllegalStateException -> message
        ?.takeIf { it.any { char -> char in '一'..'龥' } }
        ?: fallback
    else -> fallback
}
