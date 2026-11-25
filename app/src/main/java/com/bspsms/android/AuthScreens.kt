package com.bspsms.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthGateScreen(
    repo: AuthRepository,
    deviceId: String,
    locale: AppLocale,
    initialTab: AuthTab = AuthTab.GENERAL_LOGIN,
    initialErrorMessage: String? = null,
    onClearInitialError: () -> Unit = {},
    onChangeLocale: (AppLocale) -> Unit,
    onAuthenticated: (AuthSession) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var selectedTab by remember(initialTab, initialErrorMessage) { mutableStateOf(initialTab) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember(initialErrorMessage) { mutableStateOf(initialErrorMessage) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    // Register fields (NO OTP, exact A:G sheet columns)
    var generatedUserId by remember { mutableStateOf(generateNewUserId()) }
    var regPhone by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regConfirmPassword by remember { mutableStateOf("") }
    var regShowPass by remember { mutableStateOf(false) }

    // General Login fields
    var loginIdentifier by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginShowPass by remember { mutableStateOf(false) }

    // Admin Login fields
    var adminId by remember { mutableStateOf("") }
    var adminPassword by remember { mutableStateOf("") }
    var adminShowPass by remember { mutableStateOf(false) }

    val primaryBlue = Color(0xFF123B70)
    val saffronStart = Color(0xFFFF9933)
    val saffronEnd = Color(0xFFF57C00)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Language Toggle at top right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = locale == AppLocale.EN,
                    onClick = { onChangeLocale(AppLocale.EN) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("English", fontSize = 12.sp)
                }
                SegmentedButton(
                    selected = locale == AppLocale.BN,
                    onClick = { onChangeLocale(AppLocale.BN) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("বাংলা", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // PB SMS Saffron Brand Mark
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(
                    brush = Brush.linearGradient(listOf(saffronStart, saffronEnd)),
                    shape = RoundedCornerShape(18.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "PB",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    lineHeight = 22.sp
                )
                Text(
                    text = "SMS",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "BSP-SMS",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )
        Text(
            text = if (locale == AppLocale.BN) {
                "বাংলারশিক্ষা এসএমএস পোর্টাল ও অটোমেশন"
            } else {
                "Banglarshiksha SMS Portal & Automation"
            },
            fontSize = 13.sp,
            color = Color(0xFF475569),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 3 Options: Register | General Login | Admin Login
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = selectedTab == AuthTab.REGISTER,
                onClick = {
                    selectedTab = AuthTab.REGISTER
                    errorMessage = null
                    infoMessage = null
                    onClearInitialError()
                },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
            ) {
                Text(if (locale == AppLocale.BN) "রেজিস্টার" else "Register", fontSize = 12.sp, maxLines = 1)
            }
            SegmentedButton(
                selected = selectedTab == AuthTab.GENERAL_LOGIN,
                onClick = {
                    selectedTab = AuthTab.GENERAL_LOGIN
                    errorMessage = null
                    infoMessage = null
                    onClearInitialError()
                },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
            ) {
                Text(if (locale == AppLocale.BN) "লগইন" else "General Login", fontSize = 12.sp, maxLines = 1)
            }
            SegmentedButton(
                selected = selectedTab == AuthTab.ADMIN_LOGIN,
                onClick = {
                    selectedTab = AuthTab.ADMIN_LOGIN
                    errorMessage = null
                    infoMessage = null
                    onClearInitialError()
                },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
            ) {
                Text(if (locale == AppLocale.BN) "অ্যাডমিন" else "Admin Login", fontSize = 12.sp, maxLines = 1)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = Color.White,
            tonalElevation = 1.dp,
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                when (selectedTab) {
                    AuthTab.REGISTER -> {
                        Text(
                            text = if (locale == AppLocale.BN) "নতুন অ্যাকাউন্ট তৈরি করুন" else "Create Account",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (locale == AppLocale.BN) {
                                "কোনো ওটিপি প্রয়োজন নেই। রেজিস্ট্রেশনের পর অ্যাডমিন ৩০ দিনের জন্য অ্যাকাউন্ট অ্যাক্টিভ করবেন।"
                            } else {
                                "No OTP required. After registration, Admin activates your account for 30 days."
                            },
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Generated User ID Box
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF1F5F9), RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = if (locale == AppLocale.BN) "আপনার ইউজার আইডি (সংরক্ষণ করুন)" else "Your Generated User ID (Save this)",
                                fontSize = 11.sp,
                                color = Color(0xFF475569)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = generatedUserId,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = primaryBlue
                                )
                                Row {
                                    IconButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(generatedUserId))
                                            infoMessage = "Copied User ID: $generatedUserId"
                                        }
                                    ) {
                                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy User ID", tint = primaryBlue)
                                    }
                                    IconButton(
                                        onClick = { generatedUserId = generateNewUserId() }
                                    ) {
                                        Icon(Icons.Outlined.Refresh, contentDescription = "Generate new User ID", tint = primaryBlue)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = regPhone,
                            onValueChange = { regPhone = it.filter { ch -> ch.isDigit() }.take(10) },
                            label = { Text(if (locale == AppLocale.BN) "ফোন নম্বর (১০ সংখ্যা)" else "Phone Number (10 digits)") },
                            leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = regPassword,
                            onValueChange = { regPassword = it },
                            label = { Text(if (locale == AppLocale.BN) "পাসওয়ার্ড তৈরি করুন" else "Choose Password") },
                            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { regShowPass = !regShowPass }) {
                                    Icon(
                                        if (regShowPass) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = "Toggle password"
                                    )
                                }
                            },
                            visualTransformation = if (regShowPass) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = regConfirmPassword,
                            onValueChange = { regConfirmPassword = it },
                            label = { Text(if (locale == AppLocale.BN) "পাসওয়ার্ড নিশ্চিত করুন" else "Confirm Password") },
                            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                            visualTransformation = if (regShowPass) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                errorMessage = null
                                infoMessage = null
                                val cleanPhone = normalizePhoneNumber(regPhone)
                                val cleanPass = normalizePassword(regPassword)
                                val cleanConfirm = normalizePassword(regConfirmPassword)
                                val cleanUserId = normalizeUserId(generatedUserId)

                                if (cleanPhone.length < 10) {
                                    errorMessage = if (locale == AppLocale.BN) "সঠিক ১০ সংখ্যার ফোন নম্বর দিন।" else "Enter a valid 10-digit phone number."
                                    return@Button
                                }
                                if (cleanPass.length < 4) {
                                    errorMessage = if (locale == AppLocale.BN) "পাসওয়ার্ড কমপক্ষে ৪ অক্ষরের হতে হবে।" else "Password must be at least 4 characters."
                                    return@Button
                                }
                                if (cleanPass != cleanConfirm) {
                                    errorMessage = if (locale == AppLocale.BN) "পাসওয়ার্ড দুটি মেলেনি।" else "Passwords do not match."
                                    return@Button
                                }
                                isLoading = true
                                scope.launch {
                                    val res = repo.register(
                                        userId = cleanUserId,
                                        phone = cleanPhone,
                                        password = cleanPass,
                                        deviceId = deviceId
                                    )
                                    isLoading = false
                                    if (res.ok) {
                                        val finalId = res.user?.userId?.ifBlank { cleanUserId } ?: cleanUserId
                                        infoMessage = res.message.ifBlank {
                                            if (locale == AppLocale.BN) {
                                                "রেজিস্ট্রেশন সম্পন্ন! ইউজার আইডি: $finalId (স্ট্যাটাস: inactive)।"
                                            } else {
                                                "Registered! User ID: $finalId (Status: inactive). Contact Admin to activate for 30 days."
                                            }
                                        }
                                        loginIdentifier = finalId
                                    } else {
                                        errorMessage = res.message.ifBlank { "Registration failed (${res.code})." }
                                    }
                                }
                            },
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = primaryBlue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text(if (locale == AppLocale.BN) "রেজিস্টার করুন" else "Register Account")
                            }
                        }
                    }

                    AuthTab.GENERAL_LOGIN -> {
                        Text(
                            text = if (locale == AppLocale.BN) "সাধারণ লগইন" else "General Login",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (locale == AppLocale.BN) {
                                "আপনার ইউজার আইডি বা ফোন নম্বর এবং পাসওয়ার্ড দিয়ে লগইন করুন। প্রতিটি অ্যাকাউন্ট একটি ডিভাইসে লক থাকে।"
                            } else {
                                "Sign in with User ID or Phone Number and Password. Accounts are locked to one device."
                            },
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = loginIdentifier,
                            onValueChange = { loginIdentifier = it },
                            label = { Text(if (locale == AppLocale.BN) "ইউজার আইডি বা ফোন নম্বর" else "User ID or Phone Number") },
                            leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = loginPassword,
                            onValueChange = { loginPassword = it },
                            label = { Text(if (locale == AppLocale.BN) "পাসওয়ার্ড" else "Password") },
                            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { loginShowPass = !loginShowPass }) {
                                    Icon(
                                        if (loginShowPass) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = "Toggle password"
                                    )
                                }
                            },
                            visualTransformation = if (loginShowPass) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                errorMessage = null
                                infoMessage = null
                                onClearInitialError()
                                val cleanIdentifier = normalizeLoginIdentifier(loginIdentifier)
                                val cleanPass = normalizePassword(loginPassword)

                                if (cleanIdentifier.isBlank() || cleanPass.isBlank()) {
                                    errorMessage = if (locale == AppLocale.BN) "ইউজার আইডি/ফোন ও পাসওয়ার্ড দিন।" else "Enter User ID / Phone Number and Password."
                                    return@Button
                                }
                                isLoading = true
                                scope.launch {
                                    val res = repo.login(cleanIdentifier, cleanPass, deviceId)
                                    isLoading = false
                                    val returnedUser = res.user ?: res.users.singleOrNull()
                                    if (!res.ok) {
                                        errorMessage = res.message.ifBlank {
                                            when {
                                                returnedUser != null && returnedUser.hasStatusField && !returnedUser.isActive ->
                                                    "Your account (${returnedUser.userId}) is inactive. Contact Admin for 30-day activation."
                                                returnedUser != null && returnedUser.hasExpiryField && returnedUser.isExpired ->
                                                    "Your 1-month (30-day) subscription expired on ${returnedUser.expiryDate}. Contact Admin to renew."
                                                returnedUser != null && returnedUser.activeDeviceId.isNotBlank() && returnedUser.activeDeviceId != deviceId ->
                                                    "Account locked to another device (${returnedUser.activeDeviceId.take(8)}...). Ask Admin to reset device lock."
                                                else -> "Login failed (${res.code})."
                                            }
                                        }
                                        return@launch
                                    }

                                    when {
                                        returnedUser != null && returnedUser.hasStatusField && !returnedUser.isActive -> {
                                            errorMessage = res.message.takeIf {
                                                it.isNotBlank() && !it.contains("login successful", ignoreCase = true) && !it.equals("Done", ignoreCase = true)
                                            } ?: "Your account (${returnedUser.userId.ifBlank { cleanIdentifier }}) is inactive. Contact Admin for 30-day activation."
                                        }
                                        returnedUser != null && returnedUser.hasExpiryField && returnedUser.isExpired -> {
                                            errorMessage = res.message.takeIf {
                                                it.isNotBlank() && !it.contains("login successful", ignoreCase = true) && !it.equals("Done", ignoreCase = true)
                                            } ?: "Your 1-month (30-day) subscription expired on ${returnedUser.expiryDate.ifBlank { "N/A" }}. Contact Admin to renew."
                                        }
                                        returnedUser != null && returnedUser.activeDeviceId.isNotBlank() && returnedUser.activeDeviceId != deviceId -> {
                                            errorMessage = "Account locked to another device (${returnedUser.activeDeviceId.take(8)}...). Ask Admin to reset device lock."
                                        }
                                        else -> {
                                            val isPhoneInput = cleanIdentifier.all { it.isDigit() } && cleanIdentifier.length >= 10
                                            val session = AuthSession(
                                                isAdmin = false,
                                                userId = returnedUser?.userId?.ifBlank { cleanIdentifier.uppercase() } ?: cleanIdentifier.uppercase(),
                                                phone = returnedUser?.phone?.ifBlank { if (isPhoneInput) cleanIdentifier else "" }
                                                    ?: if (isPhoneInput) cleanIdentifier else "",
                                                expiryDate = returnedUser?.expiryDate.orEmpty(),
                                                deviceId = deviceId,
                                                sessionToken = res.sessionToken,
                                                fullName = returnedUser?.fullName.orEmpty(),
                                                username = returnedUser?.username.orEmpty(),
                                                role = returnedUser?.role?.ifBlank { "general" } ?: "general",
                                                toolPermissions = res.toolPermissions ?: returnedUser?.toolPermissions ?: ToolPermissions()
                                            )
                                            res.appConfig?.let { repo.saveAppConfig(it) }
                                            repo.saveSession(session)
                                            onAuthenticated(session)
                                        }
                                    }
                                }
                            },
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = primaryBlue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text(if (locale == AppLocale.BN) "লগইন করুন" else "Sign In")
                            }
                        }
                    }

                    AuthTab.ADMIN_LOGIN -> {
                        Text(
                            text = if (locale == AppLocale.BN) "অ্যাডমিন লগইন" else "Admin Login",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (locale == AppLocale.BN) {
                                "কোনো মেয়াদ বা সাবস্ক্রিপশন সীমাবদ্ধতা ছাড়াই সরাসরি অ্যাপ ও ইউজার মনিটরিং প্যানেল খুলুন।"
                            } else {
                                "Direct access with no subscription or expiry restriction, plus full user monitoring."
                            },
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = adminId,
                            onValueChange = { adminId = it },
                            label = { Text(if (locale == AppLocale.BN) "অ্যাডমিন আইডি" else "Admin ID") },
                            leadingIcon = { Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = adminPassword,
                            onValueChange = { adminPassword = it },
                            label = { Text(if (locale == AppLocale.BN) "অ্যাডমিন পাসওয়ার্ড" else "Admin Password") },
                            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { adminShowPass = !adminShowPass }) {
                                    Icon(
                                        if (adminShowPass) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = "Toggle password"
                                    )
                                }
                            },
                            visualTransformation = if (adminShowPass) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                errorMessage = null
                                infoMessage = null
                                if (repo.verifyAdminCredentials(adminId, adminPassword)) {
                                    val session = AuthSession(
                                        isAdmin = true,
                                        userId = AuthRepository.ADMIN_ID,
                                        phone = AuthRepository.ADMIN_ID,
                                        expiryDate = "Unlimited",
                                        deviceId = deviceId
                                    )
                                    repo.saveSession(session)
                                    onAuthenticated(session)
                                } else {
                                    errorMessage = if (locale == AppLocale.BN) {
                                        "ভুল অ্যাডমিন আইডি বা পাসওয়ার্ড।"
                                    } else {
                                        "Invalid Admin ID or Password."
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = saffronEnd),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Text(
                                text = if (locale == AppLocale.BN) "অ্যাডমিন হিসেবে প্রবেশ করুন" else "Enter as Admin",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        color = Color(0xFFFEF2F2),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = Color(0xFF991B1B),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (!infoMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        color = Color(0xFFF0FDF4),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = infoMessage!!,
                            color = Color(0xFF166534),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        OutlinedButton(
            onClick = {
                val hintId = loginIdentifier.trim().ifBlank { generatedUserId }
                val phoneHint = regPhone.trim()
                val msg = buildString {
                    append("Hello Admin, I need assistance with BSP-SMS App.")
                    if (hintId.isNotBlank()) append("\nUser ID: $hintId")
                    if (phoneHint.isNotBlank()) append("\nPhone: $phoneHint")
                }
                ContactAndMessageHelper.openAdminWhatsApp(context, msg)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Outlined.Chat,
                contentDescription = null,
                tint = Color(0xFF15803D),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (locale == AppLocale.BN) {
                    "অ্যাডমিনের সাথে যোগাযোগ করুন (WhatsApp: 9679843774)"
                } else {
                    "Admin Contact · One-Click Message (9679843774)"
                },
                fontSize = 12.sp,
                color = Color(0xFF15803D),
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Device ID: ${deviceId.take(12)}...",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Color(0xFF64748B)
        )
    }
}

@Composable
fun SubscriptionBlockedScreen(
    session: AuthSession,
    reasonMessage: String,
    locale: AppLocale,
    isChecking: Boolean,
    onRecheck: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.LockReset,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (locale == AppLocale.BN) "অ্যাক্সেস স্থগিত করা হয়েছে" else "Access Restricted",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = reasonMessage,
                    fontSize = 13.sp,
                    color = Color(0xFF475569),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "User ID: ${session.userId}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        )
                        if (session.phone.isNotBlank()) {
                            Text(
                                text = "Phone: ${session.phone}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFF334155)
                            )
                        }
                        if (session.expiryDate.isNotBlank()) {
                            Text(
                                text = "Expiry Date: ${session.expiryDate}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFF991B1B)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRecheck,
                    enabled = !isChecking,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF123B70)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (locale == AppLocale.BN) "স্ট্যাটাস পুনরায় যাচাই করুন" else "Re-check Activation Status")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val msg = "Hello Admin, please activate / renew my BSP-SMS account.\nUser ID: ${session.userId}\nPhone: ${session.phone}\nExpiry: ${session.expiryDate}"
                        ContactAndMessageHelper.openAdminWhatsApp(context, msg)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(Icons.Outlined.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (locale == AppLocale.BN) "অ্যাডমিনকে মেসেজ করুন" else "Contact Admin (One-Click WhatsApp)")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text(if (locale == AppLocale.BN) "সাইন আউট" else "Sign Out")
                }
            }
        }
    }
}
