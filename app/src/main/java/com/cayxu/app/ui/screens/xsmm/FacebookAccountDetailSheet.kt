package com.cayxu.app.ui.screens.xsmm

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.cayxu.app.data.local.FacebookAccount
import com.cayxu.app.data.local.FacebookAccountsStore
import com.cayxu.app.facebook.FacebookMediaEngine
import com.cayxu.app.ui.xsmm.XsmmAccountDetailField
import com.cayxu.app.ui.xsmm.XsmmAccountDetailSheet
import com.cayxu.app.ui.xsmm.XsmmAccountDetailUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Màn hình chi tiết tài khoản Facebook.
 * Giao diện được hiển thị thông qua [XsmmAccountDetailSheet] chuẩn Canvas,
 * toàn bộ logic nghiệp vụ (lấy ảnh, đổi Avatar, đổi Cover qua Graph API, copy clipboard, lưu Store)
 * được bảo toàn 100% nguyên bản.
 */
@Composable
fun FacebookAccountDetailSheet(
    account: FacebookAccount,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentAvatar by remember { mutableStateOf(account.avatar) }
    var currentCover by remember { mutableStateOf(account.cover) }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var isUploadingCover by remember { mutableStateOf(false) }

    // Tự động load cover photo và avatar HD khi mở Sheet nếu có token (100% logic gốc)
    LaunchedEffect(account.uid, account.bio) {
        val token = account.bio.ifBlank { "" }
        if (token.isNotBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val proxyParts = account.phone.ifBlank { null }?.split(":")
                    val proxyHost = proxyParts?.getOrNull(0)
                    val proxyPort = proxyParts?.getOrNull(1)?.toIntOrNull()
                    val mediaEngine = FacebookMediaEngine(
                        accessToken = token,
                        proxyHost = proxyHost,
                        proxyPort = proxyPort
                    )
                    val mediaInfo = mediaEngine.getProfileMedia("me", tokenParam = token)
                    if (mediaInfo != null) {
                        val fetchedCover = mediaInfo.coverUrl.orEmpty()
                        val fetchedAvatar = mediaInfo.avatarUrl.orEmpty()
                        withContext(Dispatchers.Main) {
                            if (fetchedCover.isNotBlank() && fetchedCover != currentCover) {
                                currentCover = fetchedCover
                            }
                            if (fetchedAvatar.isNotBlank() && !fetchedAvatar.contains("84628273_176159830277856")) {
                                currentAvatar = fetchedAvatar
                            }
                            val updatedAcc = account.copy(avatar = currentAvatar, cover = currentCover)
                            FacebookAccountsStore.updateAccount(context, updatedAcc)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // Launcher chọn ảnh đại diện (Avatar) qua 100% Graph API (Token) (100% logic gốc)
    val pickAvatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val token = account.bio.ifBlank { "" }
            if (token.isBlank()) {
                Toast.makeText(context, "Tài khoản cần có Access Token để đổi Avatar", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }

            isUploadingAvatar = true
            scope.launch(Dispatchers.IO) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes == null || bytes.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Không thể đọc file ảnh", Toast.LENGTH_SHORT).show()
                            isUploadingAvatar = false
                        }
                        return@launch
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Đang tải lên Avatar mới...", Toast.LENGTH_SHORT).show()
                    }

                    val proxyParts = account.phone.ifBlank { null }?.split(":")
                    val proxyHost = proxyParts?.getOrNull(0)
                    val proxyPort = proxyParts?.getOrNull(1)?.toIntOrNull()

                    val mediaEngine = FacebookMediaEngine(
                        accessToken = token,
                        proxyHost = proxyHost,
                        proxyPort = proxyPort
                    )
                    val result = mediaEngine.updateUserAvatar(
                        imageBytes = bytes,
                        tokenParam = token
                    )

                    withContext(Dispatchers.Main) {
                        isUploadingAvatar = false
                        if (result.isSuccess) {
                            Toast.makeText(context, "Đổi Avatar thành công!", Toast.LENGTH_SHORT).show()
                            var directUrl: String? = null
                            if (!result.mediaId.isNullOrBlank()) {
                                directUrl = mediaEngine.getPhotoDirectUrl(result.mediaId, tokenParam = token)
                            }
                            if (directUrl.isNullOrBlank()) {
                                val updatedMedia = mediaEngine.getUserMedia(tokenParam = token)
                                directUrl = updatedMedia?.avatarUrl?.takeIf { !it.contains("84628273_176159830277856") }
                            }
                            val newAvatarUrl = directUrl ?: "https://graph.facebook.com/v21.0/me/picture?type=large&access_token=$token&t=${System.currentTimeMillis()}"
                            currentAvatar = newAvatarUrl
                            val updatedAcc = account.copy(avatar = newAvatarUrl, cover = currentCover)
                            FacebookAccountsStore.updateAccount(context, updatedAcc)
                        } else {
                            Toast.makeText(context, "Lỗi đổi Avatar: ${result.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isUploadingAvatar = false
                        Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Launcher chọn ảnh bìa (Cover) qua 100% Graph API (Token) (100% logic gốc)
    val pickCoverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val token = account.bio.ifBlank { "" }
            if (token.isBlank()) {
                Toast.makeText(context, "Tài khoản cần có Access Token để đổi Ảnh Bìa", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }

            isUploadingCover = true
            scope.launch(Dispatchers.IO) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes == null || bytes.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Không thể đọc file ảnh", Toast.LENGTH_SHORT).show()
                            isUploadingCover = false
                        }
                        return@launch
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Đang tải lên Ảnh Bìa mới...", Toast.LENGTH_SHORT).show()
                    }

                    val proxyParts = account.phone.ifBlank { null }?.split(":")
                    val proxyHost = proxyParts?.getOrNull(0)
                    val proxyPort = proxyParts?.getOrNull(1)?.toIntOrNull()

                    val mediaEngine = FacebookMediaEngine(
                        accessToken = token,
                        proxyHost = proxyHost,
                        proxyPort = proxyPort
                    )
                    val result = mediaEngine.updateCoverPhoto(
                        imageBytes = bytes,
                        targetId = account.uid,
                        tokenParam = token
                    )

                    withContext(Dispatchers.Main) {
                        isUploadingCover = false
                        if (result.isSuccess) {
                            Toast.makeText(context, "Đổi Ảnh Bìa thành công!", Toast.LENGTH_SHORT).show()
                            kotlinx.coroutines.delay(1500)
                            val updatedMedia = mediaEngine.getProfileMedia(account.uid, tokenParam = token)
                            val rawCover = updatedMedia?.coverUrl.orEmpty()
                            val newCoverUrl = if (rawCover.isNotBlank()) {
                                if (rawCover.contains("?")) "$rawCover&t=${System.currentTimeMillis()}"
                                else "$rawCover?t=${System.currentTimeMillis()}"
                            } else if (result.mediaId != null) {
                                "https://graph.facebook.com/v21.0/${result.mediaId}/picture?access_token=$token&t=${System.currentTimeMillis()}"
                            } else {
                                currentCover
                            }
                            if (newCoverUrl != currentCover) currentCover = newCoverUrl
                            val updatedAcc = account.copy(avatar = currentAvatar, cover = currentCover)
                            FacebookAccountsStore.updateAccount(context, updatedAcc)
                        } else {
                            Toast.makeText(context, "Lỗi đổi Ảnh Bìa: ${result.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isUploadingCover = false
                        Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun copyToClipboard(label: String, text: String) {
        if (text.isBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Đã sao chép $label", Toast.LENGTH_SHORT).show()
    }

    // Xây dựng danh sách trường dữ liệu hiển thị trên giao diện mới
    val initialFields = remember(account) {
        buildList {
            // UID
            if (account.uid.isNotBlank()) {
                add(
                    XsmmAccountDetailField(
                        id = "uid",
                        label = "UID Facebook",
                        maskedValue = "••••••••••••",
                        revealedValue = account.uid,
                        revealed = false,
                        boxed = false
                    )
                )
            }
            // Email
            if (account.email.isNotBlank()) {
                add(
                    XsmmAccountDetailField(
                        id = "email",
                        label = "Email",
                        maskedValue = "••••••••••••",
                        revealedValue = account.email,
                        revealed = false,
                        boxed = false
                    )
                )
            }
            // 2FA Secret
            if (account.link.isNotBlank()) {
                add(
                    XsmmAccountDetailField(
                        id = "2fa",
                        label = "2FA Secret",
                        maskedValue = "••••••••••••",
                        revealedValue = account.link,
                        revealed = false,
                        boxed = false
                    )
                )
            }
            // Proxy
            if (account.phone.isNotBlank()) {
                add(
                    XsmmAccountDetailField(
                        id = "proxy",
                        label = "Proxy",
                        maskedValue = "••••••••••••",
                        revealedValue = account.phone,
                        revealed = false,
                        boxed = false
                    )
                )
            }
            // Cookie (dài -> boxed = true)
            if (account.note.isNotBlank()) {
                add(
                    XsmmAccountDetailField(
                        id = "cookie",
                        label = "Cookie",
                        maskedValue = "••••••••••••••••",
                        revealedValue = account.note,
                        revealed = false,
                        boxed = true
                    )
                )
            }
            // Access Token (dài -> boxed = true)
            if (account.bio.isNotBlank()) {
                add(
                    XsmmAccountDetailField(
                        id = "token",
                        label = "Access Token",
                        maskedValue = "••••••••••••••••",
                        revealedValue = account.bio,
                        revealed = false,
                        boxed = true
                    )
                )
            }
            // Danh sách Page con (hiển thị UID 615 thật)
            account.pages.forEachIndexed { index, page ->
                val uid615 = if (page.additionalProfileId.isNotBlank() && page.additionalProfileId.startsWith("615")) {
                    page.additionalProfileId
                } else if (page.pageId.startsWith("615")) {
                    page.pageId
                } else if (page.additionalProfileId.isNotBlank() && !page.additionalProfileId.equals(page.pageId, ignoreCase = true)) {
                    page.additionalProfileId
                } else {
                    page.displayUid.ifBlank { page.pageId }
                }
                add(
                    XsmmAccountDetailField(
                        id = "page_${page.pageId}_$index",
                        label = "Page: ${page.pageName.ifBlank { uid615 }}",
                        maskedValue = "••••••••••••",
                        revealedValue = uid615,
                        revealed = false,
                        boxed = false
                    )
                )
            }
        }
    }

    var fields by remember(initialFields) { mutableStateOf(initialFields) }

    val isLive = account.isLive
    val state = XsmmAccountDetailUiState(
        coverPhotoUrl = currentCover.takeIf { it.isNotBlank() },
        avatarUrl = currentAvatar,
        name = account.name.ifBlank { account.uid },
        statusLabel = if (isLive) "Live" else "Die / Checkpoint",
        statusColor = if (isLive) Color(0xFF10B981) else Color(0xFFC9363B),
        statusBg = if (isLive) Color(0xFFE7F8EE) else Color(0xFFFDECEC),
        fields = fields,
        hasPage = account.pages.isNotEmpty(),
        pageSummary = "${account.pages.size} page/profile liên kết",
    )

    XsmmAccountDetailSheet(
        state = state,
        onDismiss = onDismiss,
        onChangeCover = {
            if (isUploadingCover) {
                Toast.makeText(context, "Đang tải ảnh bìa, vui lòng chờ...", Toast.LENGTH_SHORT).show()
            } else {
                pickCoverLauncher.launch("image/*")
            }
        },
        onChangeAvatar = {
            if (isUploadingAvatar) {
                Toast.makeText(context, "Đang tải ảnh đại diện, vui lòng chờ...", Toast.LENGTH_SHORT).show()
            } else {
                pickAvatarLauncher.launch("image/*")
            }
        },
        onToggleFieldReveal = { targetField ->
            fields = fields.map {
                if (it.id == targetField.id) it.copy(revealed = !it.revealed) else it
            }
        },
        onCopyField = { targetField ->
            copyToClipboard(targetField.label, targetField.revealedValue)
        }
    )
}
