package com.cayxu.app.ui.screens.golike



import androidx.compose.animation.AnimatedVisibility

import androidx.compose.animation.animateColorAsState

import androidx.compose.animation.core.animateDpAsState

import androidx.compose.animation.core.animateFloatAsState

import androidx.compose.animation.core.tween

import androidx.compose.animation.fadeIn

import androidx.compose.animation.slideInVertically

import androidx.compose.foundation.background

import androidx.compose.foundation.border

import androidx.compose.foundation.clickable

import androidx.compose.foundation.interaction.MutableInteractionSource

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.navigationBarsPadding

import androidx.compose.foundation.layout.offset

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.layout.width

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.text.BasicText

import androidx.compose.foundation.text.BasicTextField

import androidx.compose.foundation.text.KeyboardOptions

import androidx.compose.foundation.selection.toggleable

import androidx.compose.runtime.Composable

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.shadow

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.graphics.SolidColor

import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.ui.platform.LocalView

import androidx.compose.ui.semantics.Role

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.input.KeyboardType

import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.tooling.preview.Preview

import androidx.compose.ui.unit.Dp

import androidx.compose.ui.unit.dp

import androidx.compose.ui.window.Dialog

import androidx.compose.ui.window.DialogProperties

import androidx.compose.ui.window.DialogWindowProvider

import com.cayxu.app.ui.design.Alx

import com.cayxu.app.ui.design.EaseOutSoft

import com.cayxu.app.ui.design.PrimaryButton

import com.cayxu.app.ui.design.SvgIcon

import com.cayxu.app.ui.design.type



/*

 * TẤM "CẤU HÌNH LÀM NHIỆM VỤ" CỦA GOLIKE — CHỈ LÀ LỚP GIAO DIỆN, không đụng logic.

 * Dựng đúng theo canvas GolikeConfig.dc.html (tấm trượt lên từ dưới, không phải toàn màn hình):

 * nền mờ + tấm bo góc trên, có 5 khối: thời gian nghỉ giữa các job, số job mục tiêu, thời gian lấy

 * job, thời gian hoàn thành job (2 khối mới), và tự động đổi nick khi lỗi.

 * Không gọi mạng, không tự lưu, không tự validate số — mọi giá trị + hành vi lưu đều báo ra qua

 * state/callback, bạn tự nối vào ViewModel/logic cấu hình Golike hiện có.

 *

 * Màu: tấm này đang giữ nguyên xanh dương (#1F4BD8/#E8EEFD) như bản vẽ gốc trên canvas — bản vẽ đó

 * CHƯA được đổi sang cam như các màn Golike khác. Nếu muốn đồng bộ cam giống GolikeScreen.kt /

 * GolikeAccountsScreen.kt, chỉ cần sửa 2 hằng số `GolikeCfgBlue`/`GolikeCfgBlueSoft` bên dưới.

 */



private val GolikeCfgBlue = Color(0xFF1F4BD8)

private val GolikeCfgBlueSoft = Color(0xFFE8EEFD)



private val Close = listOf("M6 6l12 12M18 6L6 18")

private val Settings = listOf(

    "M12 15a3 3 0 100-6 3 3 0 000 6z",

    "M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 11-2.83 2.83l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 11-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 11-2.83-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 110-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 112.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 114 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 112.83 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 110 4h-.09a1.65 1.65 0 00-1.51 1z",

)

/* ───────────────────────────── Trạng thái ───────────────────────────── */



data class GolikeTaskConfigUiState(

    val platformLabel: String = "TikTok",

    val breakMinSeconds: String = "8",

    val breakMaxSeconds: String = "15",

    val goalTaskCount: String = "0",

    val getJobMinSeconds: String = "5",

    val getJobMaxSeconds: String = "12",

    val completeJobMinSeconds: String = "20",

    val completeJobMaxSeconds: String = "40",

    val autoSwapOnError: Boolean = true,

    val errorSwapCount: String = "5",

)



/* ───────────────────────────── Tấm cấu hình ───────────────────────────── */



/**

 * Mở bằng [Dialog] toàn màn hình (giống các tấm XSMM khác) nên tự vẽ nền mờ + tấm trượt lên,

 * không cần route/NavHost riêng. Gọi `GolikeTaskConfigSheet(state = ..., onDismiss = ...)` ở nơi

 * đang hiện nút/màn mở cấu hình Golike.

 */

@Composable

fun GolikeTaskConfigSheet(

    state: GolikeTaskConfigUiState,

    onDismiss: () -> Unit,

    onBreakMinChange: (String) -> Unit,

    onBreakMaxChange: (String) -> Unit,

    onGoalTaskCountChange: (String) -> Unit,

    onGetJobMinChange: (String) -> Unit,

    onGetJobMaxChange: (String) -> Unit,

    onCompleteJobMinChange: (String) -> Unit,

    onCompleteJobMaxChange: (String) -> Unit,

    onAutoSwapOnErrorChange: (Boolean) -> Unit,

    onErrorSwapCountChange: (String) -> Unit,

    onSave: () -> Unit,

) {

    Dialog(

        onDismissRequest = onDismiss,

        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),

    ) {

        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)

        var shown by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) { shown = true }

        val scrim by animateFloatAsState(if (shown) 1f else 0f, tween(200), label = "golikeCfgScrim")

        val noRipple = remember { MutableInteractionSource() }



        Box(Modifier.fillMaxSize()) {

            Box(

                Modifier.fillMaxSize().graphicsLayer { alpha = scrim }.background(Color(0x730F172A))

                    .clickable(interactionSource = noRipple, indication = null, onClick = onDismiss),

            )

            AnimatedVisibility(

                visible = shown,

                modifier = Modifier.align(Alignment.BottomCenter),

                enter = slideInVertically(tween(280, easing = EaseOutSoft)) { it / 4 } + fadeIn(tween(280)),

            ) {

                Column(

                    Modifier.fillMaxWidth()

                        .background(Alx.White, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))

                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})

                        .navigationBarsPadding()

                        .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 28.dp),

                ) {

                    Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp).background(Color(0xFFD5DBE6), RoundedCornerShape(2.dp)))



                    SheetHeader(state.platformLabel, onDismiss)



                    SectionLabel("THỜI GIAN NGHỈ GIỮA CÁC JOB (GIÂY)", Modifier.padding(top = 22.dp))

                    MinMaxRow(state.breakMinSeconds, onBreakMinChange, state.breakMaxSeconds, onBreakMaxChange)



                    SectionLabel("SỐ LƯỢNG NHIỆM VỤ TỐI ĐA", Modifier.padding(top = 20.dp))

                    Column(Modifier.padding(top = 10.dp)) {

                        FieldLabel("Số job mục tiêu")

                        NumberField(state.goalTaskCount, onGoalTaskCountChange, Modifier.fillMaxWidth())

                        BasicText(

                            "Nhập 0 để không giới hạn",

                            Modifier.padding(top = 6.dp, start = 4.dp),

                            style = type(12f, FontWeight.Medium, Alx.Muted),

                        )

                    }



                    SectionLabel("THỜI GIAN LẤY JOB (GIÂY)", Modifier.padding(top = 20.dp))

                    MinMaxRow(state.getJobMinSeconds, onGetJobMinChange, state.getJobMaxSeconds, onGetJobMaxChange)

                    BasicText(

                        "Thời gian chờ để lấy một job mới từ Golike",

                        Modifier.padding(top = 6.dp, start = 4.dp),

                        style = type(12f, FontWeight.Medium, Alx.Muted),

                    )



                    SectionLabel("THỜI GIAN HOÀN THÀNH JOB (GIÂY)", Modifier.padding(top = 20.dp))

                    MinMaxRow(state.completeJobMinSeconds, onCompleteJobMinChange, state.completeJobMaxSeconds, onCompleteJobMaxChange)

                    BasicText(

                        "Thời gian giả lập để hoàn thành một job trước khi báo xong",

                        Modifier.padding(top = 6.dp, start = 4.dp),

                        style = type(12f, FontWeight.Medium, Alx.Muted),

                    )



                    Row(

                        Modifier.padding(top = 20.dp).fillMaxWidth()

                            .background(Alx.Bg, RoundedCornerShape(20.dp))

                            .toggleable(state.autoSwapOnError, role = Role.Switch, onValueChange = onAutoSwapOnErrorChange)

                            .padding(horizontal = 16.dp, vertical = 14.dp),

                        verticalAlignment = Alignment.CenterVertically,

                        horizontalArrangement = Arrangement.spacedBy(12.dp),

                    ) {

                        Column(Modifier.weight(1f)) {

                            BasicText("Tự động đổi nick khi lỗi", style = type(14.5f, FontWeight.Bold, Alx.Ink))

                            BasicText(

                                "Tự nhảy sang tài khoản tiếp theo nếu gặp lỗi liên tiếp",

                                Modifier.padding(top = 2.dp),

                                style = type(12f, FontWeight.Medium, Alx.Muted, lineHeight = 1.4f),

                            )

                        }

                        Switch(state.autoSwapOnError)

                    }



                    AnimatedVisibility(visible = state.autoSwapOnError) {

                        Column(Modifier.padding(top = 12.dp)) {

                            FieldLabel("Số lần lỗi liên tiếp thì đổi nick")

                            NumberField(state.errorSwapCount, onErrorSwapCountChange, Modifier.fillMaxWidth())

                        }

                    }



                    PrimaryButton(

                        "Lưu cấu hình", onSave,

                        modifier = Modifier.padding(top = 22.dp).fillMaxWidth(),

                    )

                }

            }

        }

    }

}



/* ───────────────────────────── Thành phần dùng trong tấm ───────────────────────────── */



@Composable

private fun SheetHeader(platformLabel: String, onClose: () -> Unit) {

    Row(Modifier.padding(top = 16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {

        Box(Modifier.size(44.dp).background(GolikeCfgBlueSoft, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {

            SvgIcon(Settings, 21.dp, GolikeCfgBlue, 1.9f)

        }

        Column(Modifier.weight(1f)) {

            BasicText("Cấu hình làm nhiệm vụ", style = type(19f, FontWeight.ExtraBold, Alx.Ink, spacing = -0.01f, lineHeight = 1.2f))

            BasicText("Áp dụng cho $platformLabel", Modifier.padding(top = 2.dp), style = type(12.5f, FontWeight.Medium, Alx.Muted))

        }

        PressBox(

            onClick = onClose,

            modifier = Modifier.size(40.dp).background(Alx.Bg, CircleShape),

            contentAlignment = Alignment.Center,

        ) { SvgIcon(Close, 18.dp, Color(0xFF475569), 2.2f) }

    }

}



@Composable

private fun SectionLabel(text: String, modifier: Modifier = Modifier) {

    BasicText(text, modifier.padding(horizontal = 4.dp), style = type(11f, FontWeight.ExtraBold, Alx.Muted, spacing = 0.12f))

}



@Composable

private fun FieldLabel(text: String) {

    BasicText(text, Modifier.padding(bottom = 6.dp, start = 4.dp), style = type(12.5f, FontWeight.SemiBold, Alx.Muted))

}



@Composable

private fun MinMaxRow(

    min: String, onMinChange: (String) -> Unit,

    max: String, onMaxChange: (String) -> Unit,

) {

    Row(Modifier.padding(top = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {

        Column(Modifier.weight(1f)) {

            FieldLabel("Tối thiểu")

            NumberField(min, onMinChange, Modifier.fillMaxWidth())

        }

        Column(Modifier.weight(1f)) {

            FieldLabel("Tối đa")

            NumberField(max, onMaxChange, Modifier.fillMaxWidth())

        }

    }

}



@Composable

private fun NumberField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {

    BasicTextField(

        value = value,

        onValueChange = { s -> onChange(s.filter { it.isDigit() }) },

        singleLine = true,

        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),

        textStyle = type(15f, FontWeight.ExtraBold, Alx.Ink),

        cursorBrush = SolidColor(GolikeCfgBlue),

        modifier = modifier.height(52.dp)

            .background(Alx.White, RoundedCornerShape(14.dp))

            .border(1.5.dp, Alx.InputBorder, RoundedCornerShape(14.dp)),

        decorationBox = { inner -> Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) { inner() } },

    )

}



/** Công tắc 52×32dp: nền xanh khi bật / #CBD2DF khi tắt, nút tròn trắng 26dp, trượt 150 ms — cùng quy cách Switch dùng ở Cài đặt / XSMM. */

@Composable

private fun Switch(checked: Boolean) {

    val track by animateColorAsState(if (checked) GolikeCfgBlue else Color(0xFFCBD2DF), tween(150), label = "track")

    val knob by animateDpAsState(if (checked) 20.dp else 0.dp, tween(150), label = "knob")

    Box(Modifier.size(width = 52.dp, height = 32.dp).background(track, RoundedCornerShape(16.dp)).padding(3.dp)) {

        Box(Modifier.offset(x = knob).size(26.dp).shadow(1.dp, CircleShape).background(Color.White, CircleShape))

    }

}



@Composable

private fun PressBox(

    onClick: () -> Unit,

    modifier: Modifier = Modifier,

    contentAlignment: Alignment = Alignment.TopStart,

    content: @Composable () -> Unit,

) {

    Box(

        modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick),

        contentAlignment = contentAlignment,

    ) { content() }

}



/* ───────────────────────────── Preview ───────────────────────────── */



@Preview(widthDp = 390, heightDp = 844, showBackground = true)

@Composable

private fun GolikeTaskConfigSheetPreview() {

    var state by remember { mutableStateOf(GolikeTaskConfigUiState()) }

    GolikeTaskConfigSheet(

        state = state,

        onDismiss = {},

        onBreakMinChange = { state = state.copy(breakMinSeconds = it) },

        onBreakMaxChange = { state = state.copy(breakMaxSeconds = it) },

        onGoalTaskCountChange = { state = state.copy(goalTaskCount = it) },

        onGetJobMinChange = { state = state.copy(getJobMinSeconds = it) },

        onGetJobMaxChange = { state = state.copy(getJobMaxSeconds = it) },

        onCompleteJobMinChange = { state = state.copy(completeJobMinSeconds = it) },

        onCompleteJobMaxChange = { state = state.copy(completeJobMaxSeconds = it) },

        onAutoSwapOnErrorChange = { state = state.copy(autoSwapOnError = it) },

        onErrorSwapCountChange = { state = state.copy(errorSwapCount = it) },

        onSave = {},

    )

}
