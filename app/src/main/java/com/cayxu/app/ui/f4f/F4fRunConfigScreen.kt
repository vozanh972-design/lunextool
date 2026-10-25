package com.cayxu.app.ui.f4f

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.cayxu.app.ui.design.Alx
import com.cayxu.app.ui.design.SvgIcon
import com.cayxu.app.ui.design.type

private val F4fViolet = Color(0xFF7C3AED)
private val F4fVioletSoft = Color(0xFFF5F3FF)
private val Back = listOf("M19 12H5M11 6l-6 6 6 6")

@Composable
fun F4fRunConfigScreen(navController: NavController) {
    val context = LocalContext.current

    var delaySeconds by remember { mutableStateOf("5") }
    var taskCount by remember { mutableStateOf("50") }
    var followTask by remember { mutableStateOf(true) }
    var likeTask by remember { mutableStateOf(true) }
    var commentTask by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Alx.Bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Alx.White, CircleShape)
                    .border(1.dp, Alx.Line, CircleShape)
                    .clickable { navController.popBackStack() },
                contentAlignment = Alignment.Center,
            ) { SvgIcon(Back, 20.dp, Alx.Ink, 2f) }
            BasicText("Cấu hình chạy Follow4Follow", style = type(20f, FontWeight.ExtraBold, Alx.Ink))
        }

        // Card Chọn nhiệm vụ
        Column(
            Modifier
                .padding(top = 20.dp)
                .fillMaxWidth()
                .background(Alx.White, RoundedCornerShape(20.dp))
                .border(1.dp, Alx.Line, RoundedCornerShape(20.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BasicText("Loại nhiệm vụ F4F", style = type(15f, FontWeight.Bold, Alx.Ink))

            ConfigCheckboxRow("Follow / Theo dõi", followTask) { followTask = !followTask }
            ConfigCheckboxRow("Like / Thả tim", likeTask) { likeTask = !likeTask }
            ConfigCheckboxRow("Comment / Bình luận", commentTask) { commentTask = !commentTask }
        }

        // Card Thời gian & Số lượng
        Column(
            Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .background(Alx.White, RoundedCornerShape(20.dp))
                .border(1.dp, Alx.Line, RoundedCornerShape(20.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BasicText("Thời gian & Giới hạn", style = type(15f, FontWeight.Bold, Alx.Ink))

            ConfigInputRow("Giãn cách giữa các task (giây)", delaySeconds) { delaySeconds = it }
            ConfigInputRow("Số lượng nhiệm vụ tối đa", taskCount) { taskCount = it }
        }

        // Nút Lưu
        Box(
            modifier = Modifier
                .padding(top = 24.dp)
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(F4fViolet)
                .clickable {
                    Toast.makeText(context, "Đã lưu cấu hình Follow4Follow", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                },
            contentAlignment = Alignment.Center
        ) {
            BasicText("Lưu cấu hình", style = type(15.5f, FontWeight.Bold, Alx.White))
        }

        Spacer(Modifier.height(30.dp))
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun ConfigCheckboxRow(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Checkbox, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        BasicText(label, style = type(14f, FontWeight.Medium, Alx.Ink))
        val shape = RoundedCornerShape(6.dp)
        Box(
            Modifier.size(22.dp)
                .then(if (checked) Modifier.background(F4fViolet, shape) else Modifier.background(Alx.White, shape).border(1.5.dp, Alx.Line, shape)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) SvgIcon(listOf("M5 13l4 4L19 7"), 12.dp, Alx.White, 2.5f)
        }
    }
}

@Composable
private fun ConfigInputRow(label: String, value: String, onValueChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        BasicText(label, Modifier.weight(1f), style = type(13.5f, FontWeight.Medium, Alx.Ink))
        Box(
            Modifier
                .size(width = 80.dp, height = 40.dp)
                .background(Alx.Bg, RoundedCornerShape(10.dp))
                .border(1.dp, Alx.Line, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = type(14f, FontWeight.Bold, Alx.Ink, align = androidx.compose.ui.text.style.TextAlign.Center),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
