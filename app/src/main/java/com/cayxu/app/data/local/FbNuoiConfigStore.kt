package com.cayxu.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.Keep
import org.json.JSONArray
import org.json.JSONObject

@Keep
data class FbNuoiConfig(
    // 1. Tương tác dạo
    val isInteractEnabled: Boolean = true,
    val selectedReactions: Set<String> = setOf("LIKE", "LOVE"),
    val interactCount: Int = 10,
    val interactDelayMinSec: Int = 3,
    val interactDelayMaxSec: Int = 8,

    // 2. Comment dạo
    val isCommentEnabled: Boolean = false,
    val commentList: List<String> = listOf(
        "Chào bạn, chúc ngày mới tốt lành!",
        "Tương tác lại với mình nhé ❤️",
        "Bài viết tuyệt vời quá bạn ơi",
        "Tuyệt vời!"
    ),
    val commentCount: Int = 3,
    val commentDelayMinSec: Int = 15,
    val commentDelayMaxSec: Int = 30,

    // 3. Follow dạo
    val isFollowEnabled: Boolean = false,
    val followCount: Int = 5,
    val followDelayMinSec: Int = 15,
    val followDelayMaxSec: Int = 30
)

object FbNuoiConfigStore {
    private const val PREFS_NAME = "cayxu_fb_nuoi_config"
    private const val KEY_CONFIG = "config_data"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun getConfig(context: Context): FbNuoiConfig {
        val raw = prefs(context).getString(KEY_CONFIG, null) ?: return FbNuoiConfig()
        return try {
            val json = JSONObject(raw)

            val reactArr = json.optJSONArray("selectedReactions")
            val reactions = mutableSetOf<String>()
            if (reactArr != null) {
                for (i in 0 until reactArr.length()) {
                    reactions.add(reactArr.getString(i))
                }
            } else {
                reactions.addAll(listOf("LIKE", "LOVE"))
            }

            val cmtArr = json.optJSONArray("commentList")
            val comments = mutableListOf<String>()
            if (cmtArr != null) {
                for (i in 0 until cmtArr.length()) {
                    val s = cmtArr.getString(i).trim()
                    if (s.isNotBlank()) comments.add(s)
                }
            }
            if (comments.isEmpty()) {
                comments.addAll(
                    listOf(
                        "Chào bạn, chúc ngày mới tốt lành!",
                        "Tương tác lại với mình nhé ❤️",
                        "Bài viết tuyệt vời quá bạn ơi",
                        "Tuyệt vời!"
                    )
                )
            }

            FbNuoiConfig(
                isInteractEnabled = json.optBoolean("isInteractEnabled", true),
                selectedReactions = if (reactions.isNotEmpty()) reactions else setOf("LIKE", "LOVE"),
                interactCount = json.optInt("interactCount", 10),
                interactDelayMinSec = json.optInt("interactDelayMinSec", 3),
                interactDelayMaxSec = json.optInt("interactDelayMaxSec", 8),

                isCommentEnabled = json.optBoolean("isCommentEnabled", false),
                commentList = comments,
                commentCount = json.optInt("commentCount", 3),
                commentDelayMinSec = json.optInt("commentDelayMinSec", 15),
                commentDelayMaxSec = json.optInt("commentDelayMaxSec", 30),

                isFollowEnabled = json.optBoolean("isFollowEnabled", false),
                followCount = json.optInt("followCount", 5),
                followDelayMinSec = json.optInt("followDelayMinSec", 15),
                followDelayMaxSec = json.optInt("followDelayMaxSec", 30)
            )
        } catch (_: Exception) {
            FbNuoiConfig()
        }
    }

    @Synchronized
    fun saveConfig(context: Context, config: FbNuoiConfig) {
        try {
            val json = JSONObject().apply {
                put("isInteractEnabled", config.isInteractEnabled)
                put("selectedReactions", JSONArray(config.selectedReactions.toList()))
                put("interactCount", config.interactCount)
                put("interactDelayMinSec", config.interactDelayMinSec)
                put("interactDelayMaxSec", config.interactDelayMaxSec)

                put("isCommentEnabled", config.isCommentEnabled)
                put("commentList", JSONArray(config.commentList.filter { it.isNotBlank() }))
                put("commentCount", config.commentCount)
                put("commentDelayMinSec", config.commentDelayMinSec)
                put("commentDelayMaxSec", config.commentDelayMaxSec)

                put("isFollowEnabled", config.isFollowEnabled)
                put("followCount", config.followCount)
                put("followDelayMinSec", config.followDelayMinSec)
                put("followDelayMaxSec", config.followDelayMaxSec)
            }
            prefs(context).edit().putString(KEY_CONFIG, json.toString()).apply()
        } catch (_: Exception) {}
    }
}
