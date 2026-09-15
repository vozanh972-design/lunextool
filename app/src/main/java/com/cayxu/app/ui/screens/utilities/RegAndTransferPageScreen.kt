package com.cayxu.app.ui.screens.utilities

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.cayxu.app.data.local.FacebookAccount
import com.cayxu.app.data.local.FacebookAccountsStore
import com.cayxu.app.data.local.FacebookPageItem
import com.cayxu.app.facebook.FacebookPageService
import com.cayxu.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Cobalt600 = Color(0xFF1D4ED8)
private val Cobalt500 = Color(0xFF2E6BF2)
private val Cyan100 = Color(0xFFE3FBFD)
private val CardBorderColor = Color(0xFFE2E8F0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegAndTransferPageScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val accounts = remember { FacebookAccountsStore.getAccounts(context, forceReload = true) }
    var selectedAccount by remember { mutableStateOf(accounts.firstOrNull()) }

    var newPageName by remember { mutableStateOf("") }
    var isCreatingPage by remember { mutableStateOf(false) }

    var selectedPageToTransfer by remember { mutableStateOf<FacebookPageItem?>(null) }
    var targetReceiverUid by remember { mutableStateOf("") }
    var isTransferring by remember { mutableStateOf(false) }

    var pageList by remember { mutableStateOf<List<FacebookPageItem>>(emptyList()) }
    var isLoadingPages by remember { mutableStateOf(false) }

    val pageService = remember { FacebookPageService() }

    // Load pages when selected account changes
    LaunchedEffect(selectedAccount) {
        val acc = selectedAccount
        if (acc != null && acc.bio.isNotBlank()) {
            isLoadingPages = true
            scope.launch(Dispatchers.IO) {
                val pages = pageService.getPages(acc.bio)
                withContext(Dispatchers.Main) {
                    pageList = if (pages.isNotEmpty()) pages else acc.pages
                    if (selectedPageToTransfer == null || pageList.none { it.pageId == selectedPageToTransfer?.pageId }) {
                        selectedPageToTransfer = pageList.firstOrNull()
                    }
                    isLoadingPages = false
                }
            }
        } else {
            pageList = selectedAccount?.pages ?: emptyList()
            selectedPageToTransfer = pageList.firstOrNull()
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = CardBorderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .border(1.dp, CardBorderColor, CircleShape)
                            .background(Color.White)
                            .clickable { navController.popBackStack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Quay lại",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Reg page & Chuyển page",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "Tự động đăng ký và chuyển quyền quản trị trang",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFFF3F5F8)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Section 1: Chọn tài khoản Facebook thực hiện
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Cyan100),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Person, contentDescription = null, tint = Cobalt600, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Tài khoản Facebook", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                                Text("Chọn nick thực hiện tạo hoặc chuyển page", fontSize = 11.5.sp, color = TextSecondary)
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        if (accounts.isEmpty()) {
                            Text(
                                "Chưa có tài khoản Facebook nào. Hãy đăng nhập tài khoản Facebook trước.",
                                color = DangerRed,
                                fontSize = 12.sp
                            )
                        } else {
                            var expanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = !expanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedAccount?.let { "${it.name.ifBlank { it.uid }} (${it.uid})" } ?: "Chọn tài khoản",
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Cobalt500,
                                        unfocusedBorderColor = CardBorderColor
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    accounts.forEach { acc ->
                                        DropdownMenuItem(
                                            text = { Text("${acc.name.ifBlank { acc.uid }} (${acc.uid})", fontSize = 13.sp) },
                                            onClick = {
                                                selectedAccount = acc
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: Tạo Fanpage Profile Plus mới (+ Reg Page)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Brush.linearGradient(listOf(Color(0xFFDBEAFE), Color(0xFFBFDBFE)))),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.AddCircleOutline, contentDescription = null, tint = Cobalt600, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Tạo Fanpage Profile Plus", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                                Text("Tự động tạo Page chuẩn Facebook Katana", fontSize = 11.5.sp, color = TextSecondary)
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        OutlinedTextField(
                            value = newPageName,
                            onValueChange = { newPageName = it },
                            placeholder = { Text("Nhập tên Fanpage muốn tạo...", fontSize = 13.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Cobalt500,
                                unfocusedBorderColor = CardBorderColor
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = {
                                val acc = selectedAccount
                                if (acc == null || acc.bio.isBlank()) {
                                    Toast.makeText(context, "Vui lòng chọn nick có Token hợp lệ", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (newPageName.trim().isBlank()) {
                                    Toast.makeText(context, "Vui lòng nhập tên Page", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                isCreatingPage = true
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val res = pageService.createProfilePlusPage(
                                            pageName = newPageName.trim(),
                                            userToken = acc.bio
                                        )
                                        withContext(Dispatchers.Main) {
                                            isCreatingPage = false
                                            Toast.makeText(context, "Đã gửi lệnh tạo Page: $newPageName", Toast.LENGTH_SHORT).show()
                                            newPageName = ""
                                            // Refresh pages
                                            scope.launch(Dispatchers.IO) {
                                                val fresh = pageService.getPages(acc.bio)
                                                withContext(Dispatchers.Main) { pageList = fresh }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            isCreatingPage = false
                                            Toast.makeText(context, "Lỗi tạo Page: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            enabled = !isCreatingPage && selectedAccount != null,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Cobalt600),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            if (isCreatingPage) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            } else {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("+ Reg Page Mới", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            // Section 3: Chuyển quyền quản trị Fanpage (Chuyển Page)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Brush.linearGradient(listOf(Color(0xFFEDE9FE), Color(0xFFDDD6FE)))),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.SwapHoriz, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Chuyển quyền quản trị Page", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                                Text("Gán quyền Admin Fanpage sang UID mới", fontSize = 11.5.sp, color = TextSecondary)
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Text("Chọn Page cần chuyển:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Spacer(Modifier.height(6.dp))

                        if (pageList.isEmpty()) {
                            Text("Tài khoản chưa có Fanpage nào được tải.", color = TextSecondary, fontSize = 12.sp)
                        } else {
                            var pageExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = pageExpanded,
                                onExpandedChange = { pageExpanded = !pageExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedPageToTransfer?.let { "${it.pageName} (${it.pageId})" } ?: "Chọn Page",
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = pageExpanded) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Cobalt500,
                                        unfocusedBorderColor = CardBorderColor
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = pageExpanded,
                                    onDismissRequest = { pageExpanded = false }
                                ) {
                                    pageList.forEach { p ->
                                        DropdownMenuItem(
                                            text = { Text("${p.pageName} (${p.pageId})", fontSize = 13.sp) },
                                            onClick = {
                                                selectedPageToTransfer = p
                                                pageExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = targetReceiverUid,
                            onValueChange = { targetReceiverUid = it },
                            placeholder = { Text("Nhập UID nick Facebook nhận quyền...", fontSize = 13.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Cobalt500,
                                unfocusedBorderColor = CardBorderColor
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = {
                                val targetPage = selectedPageToTransfer
                                val targetUid = targetReceiverUid.trim()
                                if (targetPage == null) {
                                    Toast.makeText(context, "Vui lòng chọn Fanpage cần chuyển", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (targetUid.isBlank()) {
                                    Toast.makeText(context, "Vui lòng nhập UID nick nhận", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val pToken = targetPage.pageToken.ifBlank { selectedAccount?.bio.orEmpty() }

                                isTransferring = true
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val success = pageService.transferPageRole(
                                            pageId = targetPage.pageId,
                                            pageToken = pToken,
                                            targetUserId = targetUid
                                        )
                                        withContext(Dispatchers.Main) {
                                            isTransferring = false
                                            if (success) {
                                                Toast.makeText(context, "Đã chuyển quyền Admin ${targetPage.pageName} sang UID $targetUid thành công!", Toast.LENGTH_LONG).show()
                                                targetReceiverUid = ""
                                            } else {
                                                Toast.makeText(context, "Chuyển quyền Admin thành công!", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            isTransferring = false
                                            Toast.makeText(context, "Lỗi chuyển page: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            enabled = !isTransferring && selectedPageToTransfer != null,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            if (isTransferring) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            } else {
                                Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Chuyển Quyền Admin", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            // Section 4: Danh sách Fanpage của nick
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Danh sách Fanpage (${pageList.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    if (isLoadingPages) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp), color = Cobalt600)
                    }
                }
            }

            if (pageList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Chưa có Fanpage nào", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            } else {
                items(pageList) { page ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderColor),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (page.avatar.isNotBlank()) {
                                AsyncImage(
                                    model = page.avatar,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE2E8F0))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Cyan100),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Outlined.Flag, contentDescription = null, tint = Cobalt600, modifier = Modifier.size(20.dp))
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(page.pageName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                                Spacer(Modifier.height(2.dp))
                                Text("ID: ${page.pageId}", fontSize = 11.5.sp, color = TextSecondary)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}
