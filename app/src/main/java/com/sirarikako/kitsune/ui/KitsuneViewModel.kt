package com.sirarikako.kitsune.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sirarikako.kitsune.KitsuneApplication
import com.sirarikako.kitsune.core.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class KitsuneViewModel(app: Application) : AndroidViewModel(app) {
    val repository = (app as KitsuneApplication).repository
    private val _data = MutableStateFlow<AppData?>(null)
    val data = _data.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private var loading: Job? = null
    init { load() }
    fun load() { loading?.cancel(); loading = viewModelScope.launch {
        try { repository.initialize(); repository.data.collect { _data.value = it } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { _error.value = "无法读取本地数据库，请重试；已有数据未被重置" }
    } }
    fun clearError() { _error.value = null }
    fun perform(onSuccess: () -> Unit = {}, action: suspend KitsuneRepository.() -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try { repository.action(); onSuccess() }
            catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { _error.value = e.message ?: "请检查输入" }
            catch (e: IllegalStateException) { _error.value = e.message ?: "操作未完成" }
            catch (_: Exception) { _error.value = "保存失败，请重试。本地数据未被覆盖" }
            finally { _busy.value = false }
        }
    }
}
