package com.cayxu.app.ui.overlay.xsmm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object XsmmJobStatusBridge {
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status
    fun update(text: String) { _status.value = text }
    fun clear() { _status.value = "" }
}
