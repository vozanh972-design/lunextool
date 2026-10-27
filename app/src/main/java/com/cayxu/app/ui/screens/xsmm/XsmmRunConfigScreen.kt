package com.cayxu.app.ui.screens.xsmm

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.cayxu.app.data.local.XsmmRunConfigStore
import com.cayxu.app.ui.xsmm.*

/**
 * Màn điều phối "Cấu hình chạy" XSMM:
 * Tự động mở đúng màn hình cấu hình theo nền tảng đang chọn (TikTok / Facebook / Instagram)
 * và kết nối trực tiếp với XsmmRunConfigStore để đọc / ghi dữ liệu thật.
 */
@Composable
fun XsmmRunConfigScreen(navController: NavController) {
    val context = LocalContext.current
    val activePlatform = remember { XsmmRunConfigStore.getActivePlatform(context).lowercase() }

    when (activePlatform) {
        "facebook" -> {
            val saved = remember { XsmmRunConfigStore.get(context, "facebook") }
            val taskTypes = remember(saved) { saved.effectiveTaskTypes().toSet() }

            var state by remember {
                mutableStateOf(
                    XsmmRunConfigFacebookUiState(
                        like = "facebook_like" in taskTypes,
                        love = "facebook_love" in taskTypes,
                        care = "facebook_care" in taskTypes,
                        haha = "facebook_haha" in taskTypes,
                        wow = "facebook_wow" in taskTypes,
                        sad = "facebook_sad" in taskTypes,
                        angry = "facebook_angry" in taskTypes,
                        follow = "facebook_follow" in taskTypes,
                        comment = "facebook_comment" in taskTypes,
                        share = "facebook_share" in taskTypes,
                        likePage = "facebook_likepage" in taskTypes,
                        joinGroup = "facebook_member" in taskTypes,
                        reactComment = "facebook_likecmt" in taskTypes,
                        rate = "facebook_review" in taskTypes,
                        getTaskWaitSeconds = saved.fetchTaskIntervalSeconds.toString(),
                        workSeconds = saved.doTaskDurationSeconds.toString(),
                        taskCount = if (saved.taskCountTarget > 0) saved.taskCountTarget.toString() else "",
                        stopAfterEmptyTimes = saved.stopAfterNoTaskCount.toString(),
                        errorSwapAccountCount = saved.failJobCountToSwitchAccount.toString(),
                        stopAfterDoneTimes = if (saved.stopAfterCompletedCount > 0) saved.stopAfterCompletedCount.toString() else "",
                        browseBeforeTask = saved.swipeBeforeTask,
                        backHomeThenBrowse = saved.returnHomeAndSwipe,
                    )
                )
            }

            XsmmRunConfigFacebookScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onLikeChange = { state = state.copy(like = it) },
                onLoveChange = { state = state.copy(love = it) },
                onCareChange = { state = state.copy(care = it) },
                onHahaChange = { state = state.copy(haha = it) },
                onWowChange = { state = state.copy(wow = it) },
                onSadChange = { state = state.copy(sad = it) },
                onAngryChange = { state = state.copy(angry = it) },
                onFollowChange = { state = state.copy(follow = it) },
                onCommentChange = { state = state.copy(comment = it) },
                onShareChange = { state = state.copy(share = it) },
                onLikePageChange = { state = state.copy(likePage = it) },
                onJoinGroupChange = { state = state.copy(joinGroup = it) },
                onReactCommentChange = { state = state.copy(reactComment = it) },
                onRateChange = { state = state.copy(rate = it) },
                onGetTaskWaitChange = { state = state.copy(getTaskWaitSeconds = it) },
                onWorkSecondsChange = { state = state.copy(workSeconds = it) },
                onTaskCountChange = { state = state.copy(taskCount = it) },
                onStopAfterEmptyChange = { state = state.copy(stopAfterEmptyTimes = it) },
                onErrorSwapChange = { state = state.copy(errorSwapAccountCount = it) },
                onStopAfterDoneChange = { state = state.copy(stopAfterDoneTimes = it) },
                onBrowseBeforeTaskChange = { state = state.copy(browseBeforeTask = it) },
                onBackHomeThenBrowseChange = { state = state.copy(backHomeThenBrowse = it) },
                onSave = {
                    val selectedTypes = mutableListOf<String>()
                    if (state.like) selectedTypes.add("facebook_like")
                    if (state.love) selectedTypes.add("facebook_love")
                    if (state.care) selectedTypes.add("facebook_care")
                    if (state.haha) selectedTypes.add("facebook_haha")
                    if (state.wow) selectedTypes.add("facebook_wow")
                    if (state.sad) selectedTypes.add("facebook_sad")
                    if (state.angry) selectedTypes.add("facebook_angry")
                    if (state.follow) selectedTypes.add("facebook_follow")
                    if (state.comment) selectedTypes.add("facebook_comment")
                    if (state.share) selectedTypes.add("facebook_share")
                    if (state.likePage) selectedTypes.add("facebook_likepage")
                    if (state.joinGroup) selectedTypes.add("facebook_member")
                    if (state.reactComment) selectedTypes.add("facebook_likecmt")
                    if (state.rate) selectedTypes.add("facebook_review")

                    XsmmRunConfigStore.save(
                        context,
                        saved.copy(
                            platform = "facebook",
                            taskTypes = selectedTypes,
                            taskType = selectedTypes.firstOrNull() ?: "facebook_like",
                            fetchTaskIntervalSeconds = state.getTaskWaitSeconds.toIntOrNull()?.coerceAtLeast(1) ?: 5,
                            doTaskDurationSeconds = state.workSeconds.toIntOrNull()?.coerceAtLeast(1) ?: 10,
                            taskCountTarget = state.taskCount.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                            stopAfterNoTaskCount = state.stopAfterEmptyTimes.toIntOrNull()?.coerceAtLeast(1) ?: 3,
                            failJobCountToSwitchAccount = state.errorSwapAccountCount.toIntOrNull()?.coerceAtLeast(1) ?: 5,
                            stopAfterCompletedCount = state.stopAfterDoneTimes.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                            swipeBeforeTask = state.browseBeforeTask,
                            returnHomeAndSwipe = state.backHomeThenBrowse
                        )
                    )
                    Toast.makeText(context, "Đã lưu cấu hình Facebook", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }
            )
        }
        "instagram" -> {
            val saved = remember { XsmmRunConfigStore.get(context, "instagram") }
            val currentMode = remember(saved) {
                when (saved.taskType) {
                    "instagram_follow" -> XsmmIgTaskMode.FollowOnly
                    "instagram_like" -> XsmmIgTaskMode.TymOnly
                    "instagram_comment" -> XsmmIgTaskMode.CommentOnly
                    else -> XsmmIgTaskMode.Random
                }
            }

            var state by remember {
                mutableStateOf(
                    XsmmRunConfigInstagramUiState(
                        mode = currentMode,
                        getTaskWaitSeconds = saved.fetchTaskIntervalSeconds.toString(),
                        workSeconds = saved.doTaskDurationSeconds.toString(),
                        taskCount = if (saved.taskCountTarget > 0) saved.taskCountTarget.toString() else "",
                        stopAfterEmptyTimes = saved.stopAfterNoTaskCount.toString(),
                        errorSwapAccountCount = saved.failJobCountToSwitchAccount.toString(),
                        stopAfterDoneTimes = if (saved.stopAfterCompletedCount > 0) saved.stopAfterCompletedCount.toString() else "",
                        browseBeforeTask = saved.swipeBeforeTask,
                        backHomeThenBrowse = saved.returnHomeAndSwipe,
                    )
                )
            }

            XsmmRunConfigInstagramScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onModeChange = { state = state.copy(mode = it) },
                onGetTaskWaitChange = { state = state.copy(getTaskWaitSeconds = it) },
                onWorkSecondsChange = { state = state.copy(workSeconds = it) },
                onTaskCountChange = { state = state.copy(taskCount = it) },
                onStopAfterEmptyChange = { state = state.copy(stopAfterEmptyTimes = it) },
                onErrorSwapChange = { state = state.copy(errorSwapAccountCount = it) },
                onStopAfterDoneChange = { state = state.copy(stopAfterDoneTimes = it) },
                onBrowseBeforeTaskChange = { state = state.copy(browseBeforeTask = it) },
                onBackHomeThenBrowseChange = { state = state.copy(backHomeThenBrowse = it) },
                onSave = {
                    val targetType = when (state.mode) {
                        XsmmIgTaskMode.FollowOnly -> "instagram_follow"
                        XsmmIgTaskMode.TymOnly -> "instagram_like"
                        XsmmIgTaskMode.Random -> "instagram_random"
                    }

                    XsmmRunConfigStore.save(
                        context,
                        saved.copy(
                            platform = "instagram",
                            taskType = targetType,
                            taskTypes = listOf(targetType),
                            fetchTaskIntervalSeconds = state.getTaskWaitSeconds.toIntOrNull()?.coerceAtLeast(1) ?: 5,
                            doTaskDurationSeconds = state.workSeconds.toIntOrNull()?.coerceAtLeast(1) ?: 10,
                            taskCountTarget = state.taskCount.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                            stopAfterNoTaskCount = state.stopAfterEmptyTimes.toIntOrNull()?.coerceAtLeast(1) ?: 3,
                            failJobCountToSwitchAccount = state.errorSwapAccountCount.toIntOrNull()?.coerceAtLeast(1) ?: 5,
                            stopAfterCompletedCount = state.stopAfterDoneTimes.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                            swipeBeforeTask = state.browseBeforeTask,
                            returnHomeAndSwipe = state.backHomeThenBrowse
                        )
                    )
                    Toast.makeText(context, "Đã lưu cấu hình Instagram", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }
            )
        }
        else -> { // "tiktok"
            val saved = remember { XsmmRunConfigStore.get(context, "tiktok") }
            val taskTypes = remember(saved) { saved.effectiveTaskTypes().toSet() }

            var state by remember {
                mutableStateOf(
                    XsmmRunConfigTiktokUiState(
                        follow = "tiktok_follow" in taskTypes,
                        like = "tiktok_like" in taskTypes,
                        comment = "tiktok_comment" in taskTypes,
                        getTaskWaitSeconds = saved.fetchTaskIntervalSeconds.toString(),
                        workSeconds = saved.doTaskDurationSeconds.toString(),
                        taskCount = if (saved.taskCountTarget > 0) saved.taskCountTarget.toString() else "",
                        stopAfterEmptyTimes = saved.stopAfterNoTaskCount.toString(),
                        errorSwapAccountCount = saved.failJobCountToSwitchAccount.toString(),
                        stopAfterDoneTimes = if (saved.stopAfterCompletedCount > 0) saved.stopAfterCompletedCount.toString() else "",
                        browseBeforeTask = saved.swipeBeforeTask,
                        backHomeThenBrowse = saved.returnHomeAndSwipe,
                    )
                )
            }

            XsmmRunConfigTiktokScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onFollowChange = { state = state.copy(follow = it) },
                onLikeChange = { state = state.copy(like = it) },
                onCommentChange = { state = state.copy(comment = it) },
                onGetTaskWaitChange = { state = state.copy(getTaskWaitSeconds = it) },
                onWorkSecondsChange = { state = state.copy(workSeconds = it) },
                onTaskCountChange = { state = state.copy(taskCount = it) },
                onStopAfterEmptyChange = { state = state.copy(stopAfterEmptyTimes = it) },
                onErrorSwapChange = { state = state.copy(errorSwapAccountCount = it) },
                onStopAfterDoneChange = { state = state.copy(stopAfterDoneTimes = it) },
                onBrowseBeforeTaskChange = { state = state.copy(browseBeforeTask = it) },
                onBackHomeThenBrowseChange = { state = state.copy(backHomeThenBrowse = it) },
                onSave = {
                    val selectedTypes = mutableListOf<String>()
                    if (state.follow) selectedTypes.add("tiktok_follow")
                    if (state.like) selectedTypes.add("tiktok_like")
                    if (state.comment) selectedTypes.add("tiktok_comment")

                    XsmmRunConfigStore.save(
                        context,
                        saved.copy(
                            platform = "tiktok",
                            taskTypes = selectedTypes,
                            taskType = selectedTypes.firstOrNull() ?: "tiktok_follow",
                            fetchTaskIntervalSeconds = state.getTaskWaitSeconds.toIntOrNull()?.coerceAtLeast(1) ?: 5,
                            doTaskDurationSeconds = state.workSeconds.toIntOrNull()?.coerceAtLeast(1) ?: 10,
                            taskCountTarget = state.taskCount.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                            stopAfterNoTaskCount = state.stopAfterEmptyTimes.toIntOrNull()?.coerceAtLeast(1) ?: 3,
                            failJobCountToSwitchAccount = state.errorSwapAccountCount.toIntOrNull()?.coerceAtLeast(1) ?: 5,
                            stopAfterCompletedCount = state.stopAfterDoneTimes.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                            swipeBeforeTask = state.browseBeforeTask,
                            returnHomeAndSwipe = state.backHomeThenBrowse
                        )
                    )
                    Toast.makeText(context, "Đã lưu cấu hình TikTok", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }
            )
        }
    }
}
