package com.cayxu.app.ui.screens.linkaccount.facebook

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cayxu.app.data.local.FacebookAccount
import com.cayxu.app.data.local.FacebookAccountsStore
import com.cayxu.app.ui.theme.AppBackground
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.InfoBlueBg
import com.cayxu.app.ui.theme.Primary
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary

/**
 * Màn hình thêm tài khoản Facebook - RIÊNG BIỆT, không dùng chung với AddAccountScreen
 * (TikTok/Instagram/...). Mọi thay đổi cho Facebook chỉ sửa ở đây.
 *
 * Chỉ có các trường CÔNG KHAI: UID, Tên, Link trang cá nhân, Ghi chú - KHÔNG có ô mật khẩu/
 * 2FA/cookie/token/proxy, không thu thập thông tin đăng nhập hay hạ tầng ẩn danh của bất kỳ ai.
 *
 * Có 2 chế độ: nhập 1 tài khoản (UID + tên + link + ghi chú) hoặc nhập nhiều UID cùng lúc.
 */
@Composable
fun FacebookAddAccountScreen(navController: NavController) {
    val context = LocalContext.current
    var tabIndex by remember { mutableIntStateOf(0) }

    var singleUid by remember { mutableStateOf("") }
    var singleName by remember { mutableStateOf("") }
    var singleLink by remember { mutableStateOf("") }
    var singleNote by remember { mutableStateOf("") }
    var singleBio by remember { mutableStateOf("") }
    var singlePhone by remember { mutableStateOf("") }

    var multiUid by remember { mutableStateOf("") }
    // Danh sách các trường được chọn cho chế độ "Nhập nhiều UID", theo ĐÚNG thứ tự người dùng
    // bấm chọn -> đó cũng là thứ tự phân tách bằng dấu "|" trên mỗi dòng. UID luôn bắt buộc có.
    var multiSelectedFields by remember { mutableStateOf(listOf(FieldKey.UID)) }

    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 20.dp)) {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
            }
            Text(
                "Thêm tài khoản Facebook",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Banner thông tin
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = InfoBlueBg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Badge, contentDescription = null, tint = Primary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Chỉ cần nhập UID (mã định danh công khai) của tài khoản Facebook, không cần mật khẩu.",
                        fontSize = 12.5.sp,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Bộ chọn 2 chế độ, dạng viên thuốc nhỏ gọn thay cho TabRow to chiếm nhiều chỗ.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardWhite)
                    .padding(4.dp)
            ) {
                SegmentButton(
                    text = "Nhập UID",
                    selected = tabIndex == 0,
                    modifier = Modifier.weight(1f),
                    onClick = { tabIndex = 0 }
                )
                Spacer(Modifier.width(4.dp))
                SegmentButton(
                    text = "Nhập nhiều UID",
                    selected = tabIndex == 1,
                    modifier = Modifier.weight(1f),
                    onClick = { tabIndex = 1 }
                )
            }

            Spacer(Modifier.height(20.dp))

            if (tabIndex == 0) {
                Text("UID", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = singleUid,
                    onValueChange = { singleUid = it },
                    placeholder = { Text("Nhập UID tài khoản Facebook") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardWhite,
                        unfocusedContainerColor = CardWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "UID là mã định danh công khai của tài khoản, không phải mật khẩu.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )

                Spacer(Modifier.height(16.dp))
                Text("Tên", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = singleName,
                    onValueChange = { singleName = it },
                    placeholder = { Text("Nhập tên hiển thị (không bắt buộc)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardWhite,
                        unfocusedContainerColor = CardWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))
                Text("Link", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = singleLink,
                    onValueChange = { singleLink = it },
                    placeholder = { Text("Nhập link trang cá nhân (không bắt buộc)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardWhite,
                        unfocusedContainerColor = CardWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Link là địa chỉ công khai của trang cá nhân, ví dụ facebook.com/ten-trang.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )

                Spacer(Modifier.height(16.dp))
                Text("Ghi chú", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = singleNote,
                    onValueChange = { singleNote = it },
                    placeholder = { Text("Ghi chú thêm về tài khoản (không bắt buộc)") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardWhite,
                        unfocusedContainerColor = CardWhite
                    ),
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                )

                Spacer(Modifier.height(16.dp))
                Text("Mô tả / tiểu sử trang", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = singleBio,
                    onValueChange = { singleBio = it },
                    placeholder = { Text("Mô tả hoặc tiểu sử công khai của trang (không bắt buộc)") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardWhite,
                        unfocusedContainerColor = CardWhite
                    ),
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                )

                Spacer(Modifier.height(16.dp))
                Text("Số điện thoại liên kết", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = singlePhone,
                    onValueChange = { singlePhone = it },
                    placeholder = { Text("Nhập số điện thoại liên kết (không bắt buộc)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardWhite,
                        unfocusedContainerColor = CardWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text("Danh sách UID", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))

                Text(
                    "Chọn các trường và thứ tự phân tách bằng dấu \"|\"",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))

                // Lưới chip cho phép bật/tắt từng trường. UID luôn bật sẵn và không thể bỏ chọn.
                // Bấm vào trường nào thì trường đó được thêm vào CUỐI thứ tự (số hiển thị trên chip
                // chính là vị trí trong định dạng phân tách bằng "|"); bấm lại lần nữa để bỏ chọn.
                val fieldRows = ALL_FIELD_OPTIONS.chunked(3)
                fieldRows.forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        row.forEachIndexed { index, option ->
                            val order = multiSelectedFields.indexOf(option.key).let { if (it >= 0) it + 1 else null }
                            FieldToggleChip(
                                label = option.label,
                                order = order,
                                locked = option.key == FieldKey.UID,
                                modifier = Modifier.weight(1f).padding(end = if (index != row.lastIndex) 6.dp else 0.dp),
                                onClick = {
                                    if (option.key == FieldKey.UID) return@FieldToggleChip
                                    multiSelectedFields = if (option.key in multiSelectedFields) {
                                        multiSelectedFields - option.key
                                    } else {
                                        multiSelectedFields + option.key
                                    }
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    "Định dạng hiện tại: " + multiSelectedFields.joinToString(" | ") { key ->
                        ALL_FIELD_OPTIONS.first { it.key == key }.label
                    },
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Primary
                )

                Spacer(Modifier.height(12.dp))
                val multiPlaceholder = buildMultiUidPlaceholder(multiSelectedFields)
                OutlinedTextField(
                    value = multiUid,
                    onValueChange = { multiUid = it },
                    placeholder = { Text(multiPlaceholder) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardWhite,
                        unfocusedContainerColor = CardWhite
                    ),
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                )
                Spacer(Modifier.height(8.dp))
                val parsedMultiAccounts = parseMultiUidInput(multiUid, multiSelectedFields)
                Text(
                    "Đã nhập ${parsedMultiAccounts.size} tài khoản. Mỗi dòng phân tách bằng dấu \"|\" theo đúng thứ tự trường đã chọn ở trên.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Button(
                onClick = {
                    if (tabIndex == 0) {
                        if (singleUid.isBlank()) {
                            Toast.makeText(context, "Vui lòng nhập UID", Toast.LENGTH_SHORT).show()
                        } else {
                            FacebookAccountsStore.addAccount(
                                context,
                                uid = singleUid,
                                name = singleName,
                                link = singleLink,
                                note = singleNote,
                                phone = singlePhone,
                                bio = singleBio
                            )
                            Toast.makeText(context, "Đã thêm tài khoản Facebook", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        }
                    } else {
                        val entries = parseMultiUidInput(multiUid, multiSelectedFields)
                        if (entries.isEmpty()) {
                            Toast.makeText(context, "Vui lòng nhập ít nhất một UID", Toast.LENGTH_SHORT).show()
                        } else {
                            FacebookAccountsStore.addAccounts(context, entries)
                            Toast.makeText(context, "Đã thêm ${entries.size} tài khoản Facebook", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Xác nhận", color = CardWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

/** Khóa định danh cho từng trường có thể chọn ở chế độ "Nhập nhiều UID". */
private enum class FieldKey { UID, NAME, LINK, NOTE, BIO, PHONE }

private data class FieldOption(val key: FieldKey, val label: String)

private val ALL_FIELD_OPTIONS = listOf(
    FieldOption(FieldKey.UID, "UID"),
    FieldOption(FieldKey.NAME, "Tên"),
    FieldOption(FieldKey.LINK, "Link"),
    FieldOption(FieldKey.NOTE, "Ghi chú"),
    FieldOption(FieldKey.BIO, "Mô tả"),
    FieldOption(FieldKey.PHONE, "SĐT")
)

private val SAMPLE_VALUES = mapOf(
    FieldKey.UID to "100000001234567",
    FieldKey.NAME to "Nguyễn Văn A",
    FieldKey.LINK to "facebook.com/ten-trang",
    FieldKey.NOTE to "Ghi chú",
    FieldKey.BIO to "Mô tả trang",
    FieldKey.PHONE to "0901234567"
)

/** Sinh dòng ví dụ cho ô placeholder dựa theo các trường và thứ tự người dùng đã chọn. */
private fun buildMultiUidPlaceholder(fields: List<FieldKey>): String {
    val exampleLine = fields.joinToString("|") { SAMPLE_VALUES[it].orEmpty() }
    return "Mỗi dòng phân tách bằng \"|\" theo đúng thứ tự đã chọn, ví dụ:\n$exampleLine"
}

/**
 * Phân tích nội dung ô "Nhập nhiều UID" theo danh sách trường và thứ tự người dùng đã chọn.
 * Mỗi dòng được tách theo dấu "|"; vị trí của từng phần tương ứng với vị trí của trường đó
 * trong danh sách [fields]. Dòng nào không có UID hợp lệ sẽ bị bỏ qua.
 */
private fun parseMultiUidInput(raw: String, fields: List<FieldKey>): List<FacebookAccount> {
    val uidPos = fields.indexOf(FieldKey.UID)
    if (uidPos < 0) return emptyList()

    return raw.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val parts = line.split("|").map { it.trim() }
            val uid = parts.getOrNull(uidPos).orEmpty()
            if (uid.isEmpty()) return@mapNotNull null

            var name = ""
            var link = ""
            var note = ""
            var bio = ""
            var phone = ""
            fields.forEachIndexed { index, key ->
                val value = parts.getOrNull(index).orEmpty()
                when (key) {
                    FieldKey.NAME -> name = value
                    FieldKey.LINK -> link = value
                    FieldKey.NOTE -> note = value
                    FieldKey.BIO -> bio = value
                    FieldKey.PHONE -> phone = value
                    FieldKey.UID -> {}
                }
            }
            FacebookAccount(uid = uid, name = name, link = link, note = note, phone = phone, bio = bio)
        }
}

@Composable
private fun FieldToggleChip(
    label: String,
    order: Int?,
    locked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val selected = order != null
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Primary else CardWhite)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !locked,
                onClick = onClick
            )
            .padding(vertical = 9.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (selected) "$order. $label" else label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) CardWhite else TextSecondary
        )
    }
}

@Composable
private fun SegmentButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Primary else CardWhite)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) CardWhite else TextSecondary
        )
    }
}
