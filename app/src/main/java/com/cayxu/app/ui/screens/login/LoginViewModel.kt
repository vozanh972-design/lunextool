package com.cayxu.app.ui.screens.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cayxu.app.data.local.SecurePrefs
import com.cayxu.app.data.repository.AuthRepository
import com.cayxu.app.data.repository.AuthResult
import com.cayxu.app.util.DeviceUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val keyInput: String = "",
    val isLoading: Boolean = false,
    // true khi đang tự động kiểm tra key đã lưu lúc mở app (splash check)
    val isCheckingSavedKey: Boolean = true,
    val errorMessage: String? = null
)

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AuthRepository()
    private val securePrefs = SecurePrefs(application)

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        checkSavedKey()
    }

    /** Mỗi lần mở app: nếu đã có key đã lưu -> tự gọi verify_key.php */
    private fun checkSavedKey() {
        val savedKey = securePrefs.getKey()
        if (savedKey.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(isCheckingSavedKey = false)
            return
        }
        viewModelScope.launch {
            val deviceId = DeviceUtils.getAndroidId(getApplication())
            when (val result = repository.verifyKey(savedKey, deviceId)) {
                is AuthResult.Success -> {
                    _uiState.value = _uiState.value.copy(isCheckingSavedKey = false)
                    _pendingAutoLoginSuccess = true
                }
                is AuthResult.ApiError, is AuthResult.NetworkError -> {
                    // Key không còn hợp lệ -> xoá và quay về Login
                    securePrefs.clearKey()
                    _uiState.value = _uiState.value.copy(isCheckingSavedKey = false)
                }
            }
        }
    }

    private var _pendingAutoLoginSuccess = false
    fun consumeAutoLoginSuccess(): Boolean {
        val v = _pendingAutoLoginSuccess
        _pendingAutoLoginSuccess = false
        return v
    }

    fun onKeyInputChange(value: String) {
        _uiState.value = _uiState.value.copy(keyInput = value, errorMessage = null)
    }

    fun login(onSuccess: () -> Unit) {
        val key = _uiState.value.keyInput.trim()
        if (key.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Vui lòng nhập key")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val deviceId = DeviceUtils.getAndroidId(getApplication())
            when (val result = repository.verifyKey(key, deviceId)) {
                is AuthResult.Success -> {
                    securePrefs.saveKey(key)
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    onSuccess()
                }
                is AuthResult.ApiError -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                is AuthResult.NetworkError -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Không thể kết nối tới máy chủ, vui lòng thử lại"
                    )
                }
            }
        }
    }
}
