package com.cayxu.app.automation.facebook.nuoi

import androidx.annotation.Keep

@Keep
enum class FbReactionType(val id: String, val typeCode: String, val label: String) {
    LIKE("1", "1", "Thích"),
    LOVE("2", "2", "Yêu thích"),
    CARE("16", "16", "Thương thương"),
    HAHA("4", "4", "Haha"),
    WOW("3", "3", "Wow"),
    SAD("7", "7", "Buồn"),
    ANGRY("8", "8", "Phẫn nộ");

    companion object {
        fun fromId(id: String): FbReactionType = values().find { 
            it.id == id || it.name.equals(id, ignoreCase = true) 
        } ?: LIKE
    }
}

@Keep
data class FbPost(
    val postId: String,
    val authorName: String = "",
    val authorId: String = "",
    val message: String = "",
    val messageSnippet: String = "",
    val commentCount: Int = 0,
    val reactionCount: Int = 0,
    val reactionPickerUrl: String = "",
    val commentFormAction: String = "",
    val ftEntIdentifier: String = ""
)

@Keep
data class FbNuoiConfig(
    val isInteractEnabled: Boolean = true,
    val selectedReactions: Set<String> = setOf("1", "2"), // Like, Love (hỗ trợ cả "1", "2" lẫn "LIKE", "LOVE")
    val interactCount: Int = 10,
    val interactDelayMinSec: Int = 3,
    val interactDelayMaxSec: Int = 7,

    val isCommentEnabled: Boolean = false,
    val commentList: List<String> = listOf(
        "Chào bạn, chúc ngày mới tốt lành!",
        "Tương tác lại với mình nhé ❤️",
        "Bài viết tuyệt vời quá bạn ơi",
        "Tuyệt vời!"
    ),
    val minCommentsToComment: Int = 5, // Chỉ comment khi bài viết có từ X bình luận trở lên
    val commentCount: Int = 3,
    val commentDelayMinSec: Int = 15,
    val commentDelayMaxSec: Int = 30,

    val isFriendEnabled: Boolean = false,
    val friendCount: Int = 5,
    val friendDelayMinSec: Int = 15,
    val friendDelayMaxSec: Int = 30,

    val maxFeedPages: Int = 4 // Giả lập vuốt cuộn sâu 4 trang
)

@Keep
data class FbNuoiProgress(
    val status: String,
    val successReactions: Int = 0,
    val successComments: Int = 0,
    val successFriends: Int = 0,
    val totalErrors: Int = 0,
    val isFinished: Boolean = false,
    val errorMessage: String? = null
)
