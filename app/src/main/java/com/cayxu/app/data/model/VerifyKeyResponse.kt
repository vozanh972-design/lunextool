package com.cayxu.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * Model đại diện đầy đủ cho response trả về từ verify_key.php.
 * Phải đọc đúng toàn bộ field, không được bỏ sót.
 */
data class VerifyKeyResponse(
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String? = null,
    @SerializedName("device_locked") val deviceLocked: Boolean? = null,

    @SerializedName("package") val packageName: String? = null,
    @SerializedName("max_days") val maxDays: Int? = null,
    @SerializedName("days_left") val daysLeft: Int? = null,
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("expire_in_seconds") val expireInSeconds: Long? = null,
    @SerializedName("server_ts") val serverTs: Long? = null,
    @SerializedName("expire_ts") val expireTs: Long? = null,
    @SerializedName("device_check_count") val deviceCheckCount: Int? = null
) {
    val isSuccess: Boolean get() = status == "success"
}
