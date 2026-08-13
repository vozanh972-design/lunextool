package com.cayxu.app.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cayxu.app.data.local.FacebookAccountsStore
import com.cayxu.app.data.local.SecurePrefs
import com.cayxu.app.data.local.TikTokAccountStatus
import com.cayxu.app.data.local.TikTokAccountsStore
import com.cayxu.app.data.model.VerifyKeyResponse
import com.cayxu.app.data.repository.AuthRepository
import com.cayxu.app.data.repository.AuthResult
import com.cayxu.app.util.DeviceUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Loại hoạt động - dùng để chọn icon/màu phù hợp khi hiển thị ở mục "Hoạt động gần đây". */
enum class RecentActivityKind { TIKTOK_LINKED, FACEBOOK_LINKED }

data class RecentActivityItem(
    val kind: RecentActivityKind,
    val title: String,
    val subtitle: String,
    val timeLabel: String,
    val isHealthy: Boolean
)

data class HomeUiState(
    val androidId: String = "",
    val isLoading: Boolean = true,
    val info: VerifyKeyResponse? = null,
    val sessionExpired: Boolean = false,
    val recentActivities: List<RecentActivityItem> = emptyList()
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
                        sessionExpired = false,
                        recentActivities = loadRecentActivities()
                    )
                }
                is AuthResult.ApiError, is AuthResult.NetworkError -> {
                    securePrefs.clearKey()
                    _uiState.value = _uiState.value.copy(isLoading = false, sessionExpired = true)
                }
            }
        }
    }

    /**
     * Xây danh sách "Hoạt động gần đây" từ dữ liệu THẬT đã lưu trên máy (tài khoản TikTok
     * đã check/liên kết, tài khoản Facebook đã thêm) - thay cho danh sách hardcode cũ. Trộn
     * chung TikTok + Facebook rồi sắp theo thời gian gần nhất trước (TikTok có createdAt
     * thật; Facebook chưa có timestamp lưu sẵn nên xếp mặc định ở cuối theo thứ tự đã lưu).
     */
    private fun loadRecentActivities(): List<RecentActivityItem> {
        val context = getApplication<Application>()
        val now = System.currentTimeMillis()

        val tiktokItems = TikTokAccountsStore.getAccounts(context)
            .sortedByDescending { it.createdAt }
            .take(5)
            .map { account ->
                val label = account.handle.ifBlank { account.displayName.ifBlank { "TikTok" } }
                RecentActivityItem(
                    kind = RecentActivityKind.TIKTOK_LINKED,
                    title = "Đã liên kết tài khoản TikTok",
                    subtitle = label,
                    timeLabel = formatRelativeTime(account.createdAt, now),
                    isHealthy = account.status == TikTokAccountStatus.ACTIVE
                )
            }

        val facebookItems = FacebookAccountsStore.getAccounts(context)
            .take(5)
            .map { account ->
                RecentActivityItem(
                    kind = RecentActivityKind.FACEBOOK_LINKED,
                    title = "Đã thêm tài khoản Facebook",
                    subtitle = account.name.ifBlank { account.uid },
                    timeLabel = if (account.isLive) "Đang hoạt động" else "Đã die",
                    isHealthy = account.isLive
                )
            }

        return (tiktokItems + facebookItems).take(8)
    }

    private fun formatRelativeTime(timestampMs: Long, nowMs: Long): String {
        if (timestampMs <= 0L) return ""
        val diffMinutes = (nowMs - timestampMs) / 60_000L
        return when {
            diffMinutes < 1 -> "Vừa xong"
            diffMinutes < 60 -> "$diffMinutes phút trước"
            diffMinutes < 24 * 60 -> "${diffMinutes / 60} giờ trước"
            diffMinutes < 2 * 24 * 60 -> "Hôm qua"
            else -> {
                val fmt = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                fmt.format(java.util.Date(timestampMs))
            }
        }
    }
}
