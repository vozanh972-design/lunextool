package com.cayxu.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.cayxu.app.automation.facebook.nuoi.FbNuoiConfig
import org.json.JSONArray
import org.json.JSONObject

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
                reactions.addAll(listOf("1", "2"))
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

            val isFriend = if (json.has("isFriendEnabled")) json.optBoolean("isFriendEnabled", false)
                           else json.optBoolean("isFollowEnabled", false)
            val friendCnt = if (json.has("friendCount")) json.optInt("friendCount", 5)
                            else json.optInt("followCount", 5)
            val friendMin = if (json.has("friendDelayMinSec")) json.optInt("friendDelayMinSec", 15)
                            else json.optInt("followDelayMinSec", 15)
            val friendMax = if (json.has("friendDelayMaxSec")) json.optInt("friendDelayMaxSec", 30)
                            else json.optInt("followDelayMaxSec", 30)

            FbNuoiConfig(
                isInteractEnabled = json.optBoolean("isInteractEnabled", true),
                selectedReactions = if (reactions.isNotEmpty()) reactions else setOf("1", "2"),
                interactCount = json.optInt("interactCount", 10),
                interactDelayMinSec = json.optInt("interactDelayMinSec", 3),
                interactDelayMaxSec = json.optInt("interactDelayMaxSec", 8),

                isCommentEnabled = json.optBoolean("isCommentEnabled", false),
                commentList = comments,
                commentCount = json.optInt("commentCount", 3),
                commentDelayMinSec = json.optInt("commentDelayMinSec", 15),
                commentDelayMaxSec = json.optInt("commentDelayMaxSec", 30),

                isFriendEnabled = isFriend,
                friendCount = friendCnt,
                friendDelayMinSec = friendMin,
                friendDelayMaxSec = friendMax,

                maxFeedPages = json.optInt("maxFeedPages", 4)
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

                put("isFriendEnabled", config.isFriendEnabled)
                put("friendCount", config.friendCount)
                put("friendDelayMinSec", config.friendDelayMinSec)
                put("friendDelayMaxSec", config.friendDelayMaxSec)

                put("maxFeedPages", config.maxFeedPages)
            }
            prefs(context).edit().putString(KEY_CONFIG, json.toString()).apply()
        } catch (_: Exception) {}
    }
}
