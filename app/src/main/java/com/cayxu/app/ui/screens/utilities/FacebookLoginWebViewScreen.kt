package com.cayxu.app.ui.screens.utilities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cayxu.app.data.local.FacebookAccount
import com.cayxu.app.data.local.FacebookAccountsStore
import com.cayxu.app.ui.theme.AppBackground
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.DangerRed
import com.cayxu.app.ui.theme.Primary
import com.cayxu.app.ui.theme.SuccessGreen
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary
import com.xsmm.fbamdlogin.FbAmdLoginEngine
import com.xsmm.fbamdlogin.FbLoginResult
import com.xsmm.fbamdlogin.FbLoginState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CardBorder = Color(0x1A0F1E37)
private val BrandBlue = Color(0xFF1877F2)

data class CheckpointDialogState(
    val type: String, // "sms_code", "email_code", "twofactor_code"
    val session: String,
    val uid: String,
    val title: String
)

/**
 * Màn hình Đăng nhập Facebook (API Direct qua FbAmdLoginEngine)
 *
 * Loại bỏ hoàn toàn cơ chế WebView / URL web cũ.
 * Sử dụng 100% logic native từ FbAmdLoginEngine trích xuất từ Facebook Katana v548:
 * - Endpoint: https://b-api.facebook.com/method/auth.login
 * - App Token: 432827354065804|cb9c2da18237a3bb72878cc3a28019ad
 * - Lưu và tái sử dụng machine_id
 * - Tự động Verify token qua Graph API /me
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacebookLoginWebViewScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var emailOrPhone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var isLoggingIn by remember { mutableStateOf(false) }
    var loginResult by remember { mutableStateOf<FbLoginResult?>(null) }
    var verifyStatus by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var checkpointDialog by remember { mutableStateOf<CheckpointDialogState?>(null) }
    var otpInput by remember { mutableStateOf("") }
    var isSubmittingOtp by remember { mutableStateOf(false) }

    var savedMachineId by remember {
        mutableStateOf(FbAmdLoginEngine.getSavedMachineId(context))
    }

    fun copyToClipboard(label: String, content: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, content))
        Toast.makeText(context, "Đã sao chép $label", Toast.LENGTH_SHORT).show()
    }

    fun handleLoginSuccess(result: FbLoginResult, engine: FbAmdLoginEngine, currentPass: String, currentEmail: String) {
        // 1. Lưu machine_id để dùng lần sau, chống checkpoint thiết bị mới
        if (result.machineId.isNotBlank()) {
            FbAmdLoginEngine.saveMachineId(context, result.machineId)
            savedMachineId = result.machineId
        }

        // 2. Lưu vào cơ sở dữ liệu FacebookAccountsStore của app
        FacebookAccountsStore.addAccount(
            context = context,
            account = FacebookAccount(
                uid = result.uid,
                name = result.uid,
                bio = result.token,
                note = result.cookies,
                password = currentPass,
                email = currentEmail,
                isLive = true
            )
        )

        loginResult = result
        errorMessage = null
        isLoggingIn = false
        Toast.makeText(context, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()

        // 3. Verify token qua GET https://graph.facebook.com/me?access_token={token}
        isVerifying = true
        scope.launch(Dispatchers.IO) {
            val (isPass, msg) = engine.verifyToken(result.token)
            withContext(Dispatchers.Main) {
                isVerifying = false
                verifyStatus = if (isPass) msg else "Xác thực không thành công: $msg"
            }
        }
    }

    fun executeLogin() {
        val email = emailOrPhone.trim()
        val pass = password.trim()
        if (email.isBlank() || pass.isBlank()) {
            Toast.makeText(context, "Vui lòng nhập đầy đủ Email/UID và Mật khẩu", Toast.LENGTH_SHORT).show()
            return
        }

        focusManager.clearFocus()
        isLoggingIn = true
        errorMessage = null
        loginResult = null
        verifyStatus = null

        scope.launch(Dispatchers.IO) {
            val engine = FbAmdLoginEngine()
            val machineIdToUse = FbAmdLoginEngine.getSavedMachineId(context)
            val state = engine.login(
                emailOrPhone = email,
                password = pass,
                machineId = machineIdToUse
            )

            withContext(Dispatchers.Main) {
                when (state) {
                    is FbLoginState.Success -> {
                        handleLoginSuccess(state.result, engine, pass, email)
                    }
                    is FbLoginState.CheckpointSms -> {
                        isLoggingIn = false
                        otpInput = ""
                        checkpointDialog = CheckpointDialogState(
                            type = "sms_code",
                            session = state.checkpointSession,
                            uid = state.uid,
                            title = "Xác thực mã SMS"
                        )
                    }
                    is FbLoginState.CheckpointEmail -> {
                        isLoggingIn = false
                        otpInput = ""
                        checkpointDialog = CheckpointDialogState(
                            type = "email_code",
                            session = state.checkpointSession,
                            uid = state.uid,
                            title = "Xác thực mã Email"
                        )
                    }
                    is FbLoginState.CheckpointTwoFa -> {
                        isLoggingIn = false
                        otpInput = ""
                        checkpointDialog = CheckpointDialogState(
                            type = "twofactor_code",
                            session = state.checkpointSession,
                            uid = state.uid,
                            title = "Xác thực 2FA Authenticator"
                        )
                    }
                    is FbLoginState.Error -> {
                        isLoggingIn = false
                        errorMessage = state.message
                    }
                }
            }
        }
    }

    // Auto-parse nếu user dán định dạng uid|pass|2fa|cookie vào trường email
    fun onEmailOrPhoneChange(newVal: String) {
        if (newVal.contains("|")) {
            val (u, p, _) = FbAmdLoginEngine.parseInputLine(newVal)
            emailOrPhone = u
            if (p.isNotBlank()) {
                password = p
            }
        } else {
            emailOrPhone = newVal
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Đăng nhập Facebook",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardWhite)
            )
        },
        containerColor = AppBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card 1: Form đăng nhập
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = null,
                            tint = BrandBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Đăng nhập API Direct (Katana v548)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Text(
                        text = "Đăng nhập trực tiếp bằng giao thức Katana Mobile. Hỗ trợ nhập Email/SĐT/UID hoặc dán chuỗi định dạng UID|PASS|2FA|COOKIE.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )

                    // Machine ID Badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (savedMachineId.isNotBlank())
                                    "Thiết bị ghi nhớ: ${savedMachineId.take(16)}..."
                                else
                                    "Thiết bị: Thiết bị mới (chưa lưu)",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                            if (savedMachineId.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        FbAmdLoginEngine.saveMachineId(context, "")
                                        savedMachineId = ""
                                        Toast.makeText(context, "Đã làm mới thiết bị", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text("Làm mới", fontSize = 11.sp, color = BrandBlue)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // TextField Email / UID
                    OutlinedTextField(
                        value = emailOrPhone,
                        onValueChange = { onEmailOrPhoneChange(it) },
                        label = { Text("Email, SĐT hoặc UID Facebook") },
                        placeholder = { Text("user@gmail.com hoặc UID") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                tint = TextSecondary
                            )
                        },
                        trailingIcon = {
                            if (emailOrPhone.isNotEmpty()) {
                                IconButton(onClick = { emailOrPhone = "" }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Clear,
                                        contentDescription = "Xóa",
                                        tint = TextSecondary
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoggingIn
                    )

                    // TextField Password
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Mật khẩu") },
                        placeholder = { Text("Nhập mật khẩu Facebook") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = TextSecondary
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                    contentDescription = "Ẩn hiện mật khẩu",
                                    tint = TextSecondary
                                )
                            }
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { executeLogin() }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoggingIn
                    )

                    Spacer(Modifier.height(4.dp))

                    // Button Đăng nhập
                    Button(
                        onClick = { executeLogin() },
                        enabled = !isLoggingIn,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        if (isLoggingIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Đang xác thực với Katana...", color = Color.White, fontWeight = FontWeight.SemiBold)
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Login,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Đăng nhập Facebook", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Error Card nếu có lỗi
            if (errorMessage != null) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = DangerRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Đăng nhập thất bại",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = DangerRed
                            )
                            Text(
                                text = errorMessage ?: "",
                                fontSize = 12.sp,
                                color = Color(0xFF991B1B)
                            )
                        }
                    }
                }
            }

            // Output hiển thị chuẩn cho User khi thành công
            loginResult?.let { res ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "ĐĂNG NHẬP THÀNH CÔNG",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen
                                )
                            }

                            IconButton(
                                onClick = {
                                    val full = FbAmdLoginEngine.formatResult(res)
                                    copyToClipboard("Tất cả thông tin", full)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "Sao chép toàn bộ",
                                    tint = BrandBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Trạng thái Verify Graph API /me
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (verifyStatus?.startsWith("PASS") == true) Color(0xFFECFDF5) else Color(0xFFF1F5F9),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isVerifying) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = BrandBlue
                                    )
                                    Text(
                                        text = "Đang kiểm tra token qua Graph API /me...",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (verifyStatus?.startsWith("PASS") == true) Icons.Outlined.Verified else Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = if (verifyStatus?.startsWith("PASS") == true) SuccessGreen else TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = verifyStatus ?: "Đang xác thực token...",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (verifyStatus?.startsWith("PASS") == true) SuccessGreen else TextSecondary
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

                        // 1. UID
                        ResultRow(
                            label = "UID",
                            value = res.uid,
                            onCopy = { copyToClipboard("UID", res.uid) }
                        )

                        // 2. TOKEN EAAA
                        ResultRow(
                            label = "TOKEN EAAA",
                            value = res.token,
                            onCopy = { copyToClipboard("TOKEN EAAA", res.token) }
                        )

                        // 3. COOKIE
                        ResultRow(
                            label = "COOKIE",
                            value = res.cookies,
                            onCopy = { copyToClipboard("COOKIE", res.cookies) }
                        )

                        // Nút sao chép toàn bộ
                        OutlinedButton(
                            onClick = {
                                val full = FbAmdLoginEngine.formatResult(res)
                                copyToClipboard("Toàn bộ kết quả", full)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Sao chép UID | TOKEN | COOKIE", fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    // Dialog nhập mã OTP Checkpoint (SMS / Email / 2FA Authenticator)
    checkpointDialog?.let { cp ->
        AlertDialog(
            onDismissRequest = {
                if (!isSubmittingOtp) {
                    checkpointDialog = null
                }
            },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Pin,
                    contentDescription = null,
                    tint = BrandBlue,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = cp.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Meta yêu cầu nhập mã xác thực OTP để hoàn tất đăng nhập cho tài khoản:",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "UID: ${cp.uid}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    OutlinedTextField(
                        value = otpInput,
                        onValueChange = { otpInput = it },
                        label = { Text("Mã xác thực (OTP)") },
                        placeholder = { Text("Nhập mã 6 chữ số...") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (otpInput.isNotBlank() && !isSubmittingOtp) {
                                    // Submit
                                }
                            }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isSubmittingOtp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val code = otpInput.trim()
                        if (code.isBlank()) {
                            Toast.makeText(context, "Vui lòng nhập mã OTP", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isSubmittingOtp = true
                        scope.launch(Dispatchers.IO) {
                            val engine = FbAmdLoginEngine()
                            val nextState = if (cp.type == "twofactor_code") {
                                engine.submitTwoFaCode(cp.session, cp.uid, code)
                            } else {
                                engine.submitCheckpointCode(cp.session, cp.uid, code, cp.type)
                            }

                            withContext(Dispatchers.Main) {
                                isSubmittingOtp = false
                                checkpointDialog = null
                                when (nextState) {
                                    is FbLoginState.Success -> {
                                        handleLoginSuccess(nextState.result, engine, password, emailOrPhone)
                                    }
                                    is FbLoginState.CheckpointSms -> {
                                        otpInput = ""
                                        checkpointDialog = CheckpointDialogState(
                                            type = "sms_code",
                                            session = nextState.checkpointSession,
                                            uid = nextState.uid,
                                            title = "Xác thực mã SMS"
                                        )
                                    }
                                    is FbLoginState.CheckpointEmail -> {
                                        otpInput = ""
                                        checkpointDialog = CheckpointDialogState(
                                            type = "email_code",
                                            session = nextState.checkpointSession,
                                            uid = nextState.uid,
                                            title = "Xác thực mã Email"
                                        )
                                    }
                                    is FbLoginState.CheckpointTwoFa -> {
                                        otpInput = ""
                                        checkpointDialog = CheckpointDialogState(
                                            type = "twofactor_code",
                                            session = nextState.checkpointSession,
                                            uid = nextState.uid,
                                            title = "Xác thực 2FA Authenticator"
                                        )
                                    }
                                    is FbLoginState.Error -> {
                                        errorMessage = nextState.message
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isSubmittingOtp && otpInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                ) {
                    if (isSubmittingOtp) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Đang xác thực...")
                    } else {
                        Text("Xác nhận OTP")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { checkpointDialog = null },
                    enabled = !isSubmittingOtp
                ) {
                    Text("Hủy", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun ResultRow(
    label: String,
    value: String,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy $label",
                    tint = BrandBlue,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFF8FAFC),
            border = BorderStroke(0.5.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = value,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                maxLines = 3
            )
        }
    }
}
