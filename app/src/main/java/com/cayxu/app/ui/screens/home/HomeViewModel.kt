package com.cayxu.app.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cayxu.app.data.local.SecurePrefs
import com.cayxu.app.data.model.VerifyKeyResponse
import com.cayxu.app.data.repository.AuthRepository
import com.cayxu.app.data.repository.AuthResult
import com.cayxu.app.util.DeviceUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val androidId: String = "",
    val isLoading: Boolean = true,
    val info: VerifyKeyResponse? = null,
    val sessionExpired: Boolean = false
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AuthRepository()
    private val securePrefs = SecurePrefs(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadAccountInfo()
    }

    fun loadAccountInfo() {
        val androidId = DeviceUtils.getAndroidId(getApplication())
        val savedKey = securePrefs.getKey()

        _uiState.value = _uiState.value.copy(androidId = androidId, isLoading = true)

        if (savedKey.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(isLoading = false, sessionExpired = true)
            return
        }

        viewModelScope.launch {
            when (val result = repository.verifyKey(savedKey, androidId)) {
                is AuthResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        info = result.data,
                        sessionExpired = false
                    )
                }
                is AuthResult.ApiError, is AuthResult.NetworkError -> {
                    securePrefs.clearKey()
                    _uiState.value = _uiState.value.copy(isLoading = false, sessionExpired = true)
                }
            }
        }
    }
}
