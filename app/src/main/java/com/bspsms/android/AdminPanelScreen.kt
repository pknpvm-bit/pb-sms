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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import android.widget.Toast
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private enum class UserFilter { ALL, ACTIVE, INACTIVE, EXPIRED, ONLINE }

@Composable
fun AdminPanelScreen(
    repo: AuthRepository,
    locale: AppLocale
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<UserRecord>>(emptyList()) }
    var backendStats by remember { mutableStateOf<AdminStats?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var busyUserId by remember { mutableStateOf<String?>(null) }
    var statusBanner by remember { mutableStateOf<String?>(null) }
    var isErrorBanner by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(UserFilter.ALL) }

    // Admin management dialog states
    var editingUser by remember { mutableStateOf<UserRecord?>(null) }
    var editPhone by remember { mutableStateOf("") }

    var passwordResetUser by remember { mutableStateOf<UserRecord?>(null) }
    var newPasswordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }

    var deletingUser by remember { mutableStateOf<UserRecord?>(null) }

    var showBroadcastDialog by remember { mutableStateOf(false) }
    var broadcastMessage by remember { mutableStateOf("") }

    var appConfig by remember { mutableStateOf(repo.loadSavedAppConfig()) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var cfgPortalUrl by remember { mutableStateOf(appConfig.portalUrl) }
    var cfgMinVersion by remember { mutableStateOf(appConfig.minimumVersionCode.toString()) }
    var cfgLatestName by remember { mutableStateOf(appConfig.latestVersionName) }
    var cfgApkUrl by remember { mutableStateOf(appConfig.apkDownloadUrl) }
    var cfgUpdateMsg by remember { mutableStateOf(appConfig.updateMessage) }
    var cfgForceUpdate by remember { mutableStateOf(appConfig.forceUpdate) }
    var isSavingConfig by remember { mutableStateOf(false) }

    var permissionsUser by remember { mutableStateOf<UserRecord?>(null) }
    var draftPermissions by remember { mutableStateOf(ToolPermissions()) }

    fun showActionFeedback(message: String, isError: Boolean) {
        statusBanner = message
        isErrorBanner = isError
        if (message.isNotBlank()) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun applyUserUpdate(oldUser: UserRecord, updated: UserRecord?) {
        if (updated != null) {
            users = users.map {
                if ((oldUser.userId.isNotBlank() && it.userId == oldUser.userId) ||
                    (oldUser.phone.isNotBlank() && it.phone == oldUser.phone)
                ) {
                    updated
                } else {
                    it
                }
            }
        }
    }

    fun loadUsers(preserveBanner: Boolean = false) {
        if (!preserveBanner) {
            isLoading = true
        }
        scope.launch {
            val res = repo.adminListUsers()
            isLoading = false
            if (res.ok) {
                users = res.users
                backendStats = res.stats
                res.appConfig?.let { appConfig = it }
                if (!preserveBanner) {
                    statusBanner = res.message
                    isErrorBanner = false
                }
            } else {
                statusBanner = res.message
                isErrorBanner = true
            }
        }
    }

    LaunchedEffect(Unit) {
        loadUsers()
    }

    val computedTotal = if (users.isNotEmpty()) users.size else (backendStats?.totalGeneralUsers ?: 0)
    val computedActive = if (users.isNotEmpty()) users.count { it.isActive && !it.isExpired } else (backendStats?.activeUsers ?: 0)
    val computedInactive = if (users.isNotEmpty()) users.count { !it.isActive } else (backendStats?.inactiveUsers ?: 0)
    val computedOnline = if (users.isNotEmpty()) users.count { it.appLoggedIn && it.isActive && !it.isExpired } else (backendStats?.loggedInAppUsers ?: 0)

    val filteredUsers = remember(users, searchQuery, filter) {
        val q = searchQuery.trim().lowercase()
        users.filter { user ->
            val matchesFilter = when (filter) {
                UserFilter.ALL -> true
                UserFilter.ACTIVE -> user.isActive && !user.isExpired
                UserFilter.INACTIVE -> !user.isActive
                UserFilter.EXPIRED -> user.isActive && user.isExpired
                UserFilter.ONLINE -> user.appLoggedIn && user.isActive && !user.isExpired
            }
            val matchesSearch = q.isEmpty() ||
                user.fullName.lowercase().contains(q) ||
                user.username.lowercase().contains(q) ||
                user.userId.lowercase().contains(q) ||
                user.phone.lowercase().contains(q) ||
                user.activeDeviceId.lowercase().contains(q)
            matchesFilter && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (locale == AppLocale.BN) "অ্যাডমিন ড্যাশবোর্ড ও ইউজার মনিটর" else "Admin Dashboard & User Monitor",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "Live Google Sheet Sync · 30-Day Subscription Control",
                    fontSize = 11.sp,
                    color = Color(0xFF475569)
                )
            }
            Row {
                IconButton(
                    onClick = {
                        cfgPortalUrl = appConfig.portalUrl
                        cfgMinVersion = appConfig.minimumVersionCode.toString()
                        cfgLatestName = appConfig.latestVersionName
                        cfgApkUrl = appConfig.apkDownloadUrl
                        cfgUpdateMsg = appConfig.updateMessage
                        cfgForceUpdate = appConfig.forceUpdate
                        showConfigDialog = true
                    }
                ) {
                    Icon(Icons.Outlined.Settings, contentDescription = "App Config & Updates", tint = Color(0xFF0F172A))
                }
                IconButton(onClick = { showBroadcastDialog = true }) {
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "One-click message broadcast", tint = Color(0xFF15803D))
                }
                IconButton(onClick = { loadUsers() }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh users", tint = Color(0xFF123B70))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Admin Dashboard Summary Statistics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatSummaryCard(
                title = if (locale == AppLocale.BN) "মোট ইউজার" else "Total Users",
                value = computedTotal.toString(),
                accent = Color(0xFF123B70),
                modifier = Modifier.weight(1f)
            )
            StatSummaryCard(
                title = if (locale == AppLocale.BN) "সক্রিয়" else "Active",
                value = computedActive.toString(),
                accent = Color(0xFF15803D),
                modifier = Modifier.weight(1f)
            )
            StatSummaryCard(
                title = if (locale == AppLocale.BN) "নিষ্ক্রিয়" else "Inactive",
                value = computedInactive.toString(),
                accent = Color(0xFFB45309),
                modifier = Modifier.weight(1f)
            )
            StatSummaryCard(
                title = if (locale == AppLocale.BN) "লগইন আছে" else "Logged In",
                value = computedOnline.toString(),
                accent = Color(0xFF0369A1),
                modifier = Modifier.weight(1f)
            )
        }

        if (!statusBanner.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = if (isErrorBanner) Color(0xFFFEF2F2) else Color(0xFFF0FDF4),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (isErrorBanner) Color(0xFFFECACA) else Color(0xFFBBF7D0),
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Text(
                    text = statusBanner!!,
                    color = if (isErrorBanner) Color(0xFF991B1B) else Color(0xFF166534),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search name, username, User ID, phone, or device...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = filter == UserFilter.ALL,
                    onClick = { filter = UserFilter.ALL },
                    label = { Text("All ($computedTotal)", fontSize = 12.sp) }
                )
            }
            item {
                FilterChip(
                    selected = filter == UserFilter.ACTIVE,
                    onClick = { filter = UserFilter.ACTIVE },
                    label = { Text("Active ($computedActive)", fontSize = 12.sp) }
                )
            }
            item {
                FilterChip(
                    selected = filter == UserFilter.INACTIVE,
                    onClick = { filter = UserFilter.INACTIVE },
                    label = { Text("Inactive ($computedInactive)", fontSize = 12.sp) }
                )
            }
            item {
                FilterChip(
                    selected = filter == UserFilter.ONLINE,
                    onClick = { filter = UserFilter.ONLINE },
                    label = { Text("Logged In ($computedOnline)", fontSize = 12.sp) }
                )
            }
            item {
                FilterChip(
                    selected = filter == UserFilter.EXPIRED,
                    onClick = { filter = UserFilter.EXPIRED },
                    label = { Text("Expired", fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = Color(0xFF123B70))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Loading users from backend...", fontSize = 12.sp, color = Color(0xFF475569))
            }
        } else if (filteredUsers.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = when {
                            isErrorBanner && !statusBanner.isNullOrBlank() ->
                                "Could not load users: ${statusBanner!!}"
                            users.isEmpty() ->
                                "No registered users found in Google Sheet yet. Tap Refresh to reload."
                            else ->
                                "No users match your current search or status filter."
                        },
                        fontSize = 13.sp,
                        color = if (isErrorBanner) Color(0xFF991B1B) else Color(0xFF475569)
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(filteredUsers, key = { index, u -> "${index}_${u.userId}_${u.phone}" }) { _, user ->
                    val targetId = user.userId.ifBlank { user.phone }
                    val isBusy = busyUserId == targetId
                    val statusColor = when {
                        !user.isActive -> Color(0xFFB45309)
                        user.isExpired -> Color(0xFFDC2626)
                        else -> Color(0xFF15803D)
                    }
                    val statusText = when {
                        !user.isActive -> "Inactive"
                        user.isExpired -> "Expired (${user.expiryDate.ifBlank { "N/A" }})"
                        else -> "Active until ${user.expiryDate}"
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.displayName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color(0xFF0F172A),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = listOfNotNull(
                                            user.userId.takeIf { it.isNotBlank() }?.let { "ID: $it" },
                                            user.username.takeIf { it.isNotBlank() }?.let { "@$it" },
                                            user.phone.takeIf { it.isNotBlank() }
                                        ).joinToString(" · "),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = Color(0xFF334155)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = statusText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = statusColor
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SessionBadge(
                                    text = if (user.appLoggedIn && user.isActive && !user.isExpired) {
                                        "App: Logged In"
                                    } else {
                                        "App: Offline"
                                    },
                                    active = user.appLoggedIn && user.isActive && !user.isExpired
                                )
                                SessionBadge(
                                    text = if (user.activeDeviceId.isNotBlank()) {
                                        "Device Locked"
                                    } else {
                                        "Device Unlocked"
                                    },
                                    active = user.activeDeviceId.isNotBlank()
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Status: ${user.status} · Expiry: ${user.expiryDate.ifBlank { "Not set" }}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFF334155)
                            )
                            Text(
                                text = "Device ID: ${user.activeDeviceId.ifBlank { "Unlocked (none)" }}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFF475569)
                            )
                            Text(
                                text = "Last Login: ${user.lastLogin.ifBlank { "Never" }}",
                                fontSize = 11.sp,
                                color = Color(0xFF475569)
                            )
                            if (user.lastActivity.isNotBlank()) {
                                Text(
                                    text = "Activity: ${user.lastActivity}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                            if (isBusy) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    color = Color(0xFF123B70)
                                )
                            }

                            // Row 1: Activate / Deactivate
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    enabled = !isBusy,
                                    onClick = {
                                        busyUserId = targetId
                                        scope.launch {
                                            val res = repo.adminActivateUser(user.userId.ifBlank { targetId }, user.phone)
                                            busyUserId = null
                                            showActionFeedback(res.message, !res.ok)
                                            if (res.ok) {
                                                applyUserUpdate(user, res.user)
                                                loadUsers(preserveBanner = true)
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = when {
                                            isBusy -> "Updating..."
                                            !user.isActive || user.isExpired -> "Activate (30d)"
                                            else -> "Activate (30d)"
                                        },
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }

                                OutlinedButton(
                                    enabled = !isBusy,
                                    onClick = {
                                        busyUserId = targetId
                                        scope.launch {
                                            val res = repo.adminDeactivateUser(user.userId.ifBlank { targetId }, user.phone)
                                            busyUserId = null
                                            showActionFeedback(res.message, !res.ok)
                                            if (res.ok) {
                                                applyUserUpdate(user, res.user)
                                                loadUsers(preserveBanner = true)
                                            }
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = if (isBusy) "Updating..." else "Deactivate",
                                        fontSize = 11.sp,
                                        color = Color(0xFFDC2626),
                                        maxLines = 1
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Row 2: Reset Device, Edit Info, Reset Password, Delete
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    enabled = !isBusy,
                                    onClick = {
                                        busyUserId = targetId
                                        scope.launch {
                                            val res = repo.adminResetDevice(user.userId.ifBlank { targetId }, user.phone)
                                            busyUserId = null
                                            showActionFeedback(res.message, !res.ok)
                                            if (res.ok) {
                                                applyUserUpdate(user, res.user)
                                                loadUsers(preserveBanner = true)
                                            }
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1.25f)
                                ) {
                                    Icon(Icons.Outlined.LockReset, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Reset Device", fontSize = 11.sp, maxLines = 1)
                                }

                                OutlinedButton(
                                    enabled = !isBusy,
                                    onClick = {
                                        editingUser = user
                                        editPhone = user.phone
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(0.75f)
                                ) {
                                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Edit", fontSize = 11.sp, maxLines = 1)
                                }

                                OutlinedButton(
                                    enabled = !isBusy,
                                    onClick = {
                                        passwordResetUser = user
                                        newPasswordInput = ""
                                        confirmPasswordInput = ""
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(0.9f)
                                ) {
                                    Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Password", fontSize = 11.sp, maxLines = 1)
                                }

                                IconButton(
                                    enabled = !isBusy && user.phone.isNotBlank(),
                                    onClick = {
                                        val msg = "Hello (${user.userId}), your BSP-SMS account status: ${user.status}, Expiry: ${user.expiryDate.ifBlank { "N/A" }}."
                                        ContactAndMessageHelper.openUserWhatsApp(context, user.phone, msg)
                                    }
                                ) {
                                    Icon(
                                        Icons.Outlined.Chat,
                                        contentDescription = "Message user on WhatsApp",
                                        tint = Color(0xFF15803D),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    enabled = !isBusy,
                                    onClick = { deletingUser = user }
                                ) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = "Delete user",
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Row 3: Portal Session Controls (Force Logout & Reset Portal Session)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    enabled = !isBusy,
                                    onClick = {
                                        busyUserId = targetId
                                        scope.launch {
                                            val res = repo.adminForcePortalLogout(targetId)
                                            busyUserId = null
                                            showActionFeedback(res.message, !res.ok)
                                            if (res.ok) {
                                                applyUserUpdate(user, res.user)
                                                loadUsers(preserveBanner = true)
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.Logout, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Force Portal Out", fontSize = 11.sp, maxLines = 1)
                                }

                                OutlinedButton(
                                    enabled = !isBusy,
                                    onClick = {
                                        busyUserId = targetId
                                        scope.launch {
                                            val res = repo.adminResetPortalSession(targetId)
                                            busyUserId = null
                                            showActionFeedback(res.message, !res.ok)
                                            if (res.ok) {
                                                applyUserUpdate(user, res.user)
                                                loadUsers(preserveBanner = true)
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Reset Portal", fontSize = 11.sp, maxLines = 1)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Row 4: Tool Access Control (8 tools)
                            val disabledTools = user.toolPermissions.disabledCount()
                            val allowedCount = 8 - disabledTools
                            OutlinedButton(
                                enabled = !isBusy,
                                onClick = {
                                    permissionsUser = user
                                    draftPermissions = user.toolPermissions
                                },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (disabledTools == 0) {
                                        "Tool Permissions (All 8 Allowed)"
                                    } else {
                                        "Tool Permissions ($allowedCount/8 Allowed · $disabledTools Blocked)"
                                    },
                                    fontSize = 11.sp,
                                    color = if (disabledTools == 0) Color(0xFF123B70) else Color(0xFFB45309),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit User Info Dialog
    val currentEditTarget = editingUser
    if (currentEditTarget != null) {
        val targetId = currentEditTarget.userId.ifBlank { currentEditTarget.phone }
        AlertDialog(
            onDismissRequest = { editingUser = null },
            title = { Text("Edit User (${currentEditTarget.userId})", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it.filter { ch -> ch.isDigit() }.take(10) },
                        label = { Text("Phone Number (10 digits)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanPhone = normalizePhoneNumber(editPhone)
                        if (cleanPhone.length < 10) {
                            statusBanner = "Enter a valid 10-digit phone number."
                            isErrorBanner = true
                            return@Button
                        }
                        editingUser = null
                        busyUserId = targetId
                        scope.launch {
                            val res = repo.adminEditUser(
                                targetUserId = targetId,
                                phone = cleanPhone
                            )
                            busyUserId = null
                            showActionFeedback(res.message, !res.ok)
                            if (res.ok) {
                                applyUserUpdate(currentEditTarget, res.user)
                                loadUsers(preserveBanner = true)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF123B70))
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingUser = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Reset App Password Dialog
    val currentResetTarget = passwordResetUser
    if (currentResetTarget != null) {
        val targetId = currentResetTarget.userId.ifBlank { currentResetTarget.phone }
        AlertDialog(
            onDismissRequest = { passwordResetUser = null },
            title = { Text("Reset App Password (${currentResetTarget.userId})", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter a new app login password (minimum 4 characters). Stored securely as salted SHA-256 on the backend.",
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )
                    OutlinedTextField(
                        value = newPasswordInput,
                        onValueChange = { newPasswordInput = it },
                        label = { Text("New Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPasswordInput,
                        onValueChange = { confirmPasswordInput = it },
                        label = { Text("Confirm Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanPass = normalizePassword(newPasswordInput)
                        val cleanConfirm = normalizePassword(confirmPasswordInput)
                        if (cleanPass.length < 4 || cleanPass != cleanConfirm) {
                            statusBanner = if (cleanPass.length < 4) {
                                "New password must be at least 4 characters."
                            } else {
                                "Passwords do not match."
                            }
                            isErrorBanner = true
                            return@Button
                        }
                        passwordResetUser = null
                        busyUserId = targetId
                        scope.launch {
                            val res = repo.adminResetPassword(targetId, cleanPass)
                            busyUserId = null
                            showActionFeedback(res.message, !res.ok)
                            if (res.ok) {
                                applyUserUpdate(currentResetTarget, res.user)
                                loadUsers(preserveBanner = true)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF123B70))
                ) {
                    Text("Reset Password")
                }
            },
            dismissButton = {
                TextButton(onClick = { passwordResetUser = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete User Confirmation Dialog
    val currentDeleteTarget = deletingUser
    if (currentDeleteTarget != null) {
        val targetId = currentDeleteTarget.userId.ifBlank { currentDeleteTarget.phone }
        AlertDialog(
            onDismissRequest = { deletingUser = null },
            title = { Text("Delete Account?", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF991B1B)) },
            text = {
                Text(
                    text = "Permanently delete ${currentDeleteTarget.displayName} (${currentDeleteTarget.userId} · ${currentDeleteTarget.phone}) from the database? This cannot be undone.",
                    fontSize = 13.sp,
                    color = Color(0xFF334155)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        deletingUser = null
                        busyUserId = targetId
                        scope.launch {
                            val res = repo.adminDeleteUser(targetId)
                            busyUserId = null
                            showActionFeedback(res.message, !res.ok)
                            if (res.ok) {
                                users = users.filterNot {
                                    it.userId == currentDeleteTarget.userId && it.phone == currentDeleteTarget.phone
                                }
                                loadUsers(preserveBanner = true)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingUser = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // One-Click Admin Message / Broadcast Dialog
    if (showBroadcastDialog) {
        AlertDialog(
            onDismissRequest = { showBroadcastDialog = false },
            title = { Text("One-Click Admin Message", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Type a notice or activation message and send it in one click via WhatsApp or any messaging app.",
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )
                    OutlinedTextField(
                        value = broadcastMessage,
                        onValueChange = { broadcastMessage = it },
                        label = { Text("Message Content") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            ContactAndMessageHelper.shareBroadcastMessage(context, broadcastMessage, targetWhatsAppOnly = false)
                        }
                    ) {
                        Text("Share", fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            ContactAndMessageHelper.shareBroadcastMessage(context, broadcastMessage, targetWhatsAppOnly = true)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D))
                    ) {
                        Icon(Icons.Outlined.Chat, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("WhatsApp", fontSize = 12.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showBroadcastDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Per-User Tool Permissions Dialog
    val currentPermTarget = permissionsUser
    if (currentPermTarget != null) {
        val targetId = currentPermTarget.userId.ifBlank { currentPermTarget.phone }
        val toolLabels = remember {
            listOf(
                "formative" to "1. Formative Filler",
                "talk" to "2. Talk to Filler",
                "lpcd" to "3. LPCD Filler",
                "bco" to "4. BCO Filler",
                "excel" to "5. Excel Filler",
                "marksheet" to "6. Bulk Marksheet Downloader",
                "result_ready" to "7. Result Ready Tool",
                "hprc_scan" to "8. HPRC Scan Tool"
            )
        }
        AlertDialog(
            onDismissRequest = { permissionsUser = null },
            title = {
                Column {
                    Text("Tool Access (${currentPermTarget.userId})", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Saved in ToolPermissions sheet; enforced cleanly in app & backend", fontSize = 11.sp, color = Color(0xFF475569))
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    toolLabels.forEach { (key, label) ->
                        val checked = draftPermissions.isAllowed(key)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                color = if (checked) Color(0xFF0F172A) else Color(0xFF94A3B8),
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = checked,
                                onCheckedChange = { enabled ->
                                    draftPermissions = draftPermissions.withToggle(key, enabled)
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updatedPerms = draftPermissions
                        permissionsUser = null
                        busyUserId = targetId
                        scope.launch {
                            val res = repo.adminUpdateToolPermissions(targetId, updatedPerms, currentPermTarget.phone)
                            busyUserId = null
                            showActionFeedback(res.message, !res.ok)
                            if (res.ok) {
                                applyUserUpdate(
                                    currentPermTarget,
                                    res.user ?: currentPermTarget.copy(toolPermissions = res.toolPermissions ?: updatedPerms)
                                )
                                loadUsers(preserveBanner = true)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF123B70))
                ) {
                    Text("Save Permissions")
                }
            },
            dismissButton = {
                TextButton(onClick = { permissionsUser = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Remote AppConfig Dialog
    if (showConfigDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSavingConfig) showConfigDialog = false },
            title = { Text("Remote AppConfig (Google Sheet)", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = cfgPortalUrl,
                        onValueChange = { cfgPortalUrl = it },
                        label = { Text("Portal URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = cfgMinVersion,
                            onValueChange = { cfgMinVersion = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Min Version Code") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = cfgLatestName,
                            onValueChange = { cfgLatestName = it },
                            label = { Text("Latest Version") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = cfgApkUrl,
                        onValueChange = { cfgApkUrl = it },
                        label = { Text("APK Download URL (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = cfgUpdateMsg,
                        onValueChange = { cfgUpdateMsg = it },
                        label = { Text("Update Message") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Force Update Dialog", fontSize = 13.sp, color = Color(0xFF0F172A))
                        Switch(
                            checked = cfgForceUpdate,
                            onCheckedChange = { cfgForceUpdate = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isSavingConfig,
                    onClick = {
                        val nextCfg = AppConfigData(
                            portalUrl = cfgPortalUrl.trim().ifBlank { AppConfigData().portalUrl },
                            minimumVersionCode = cfgMinVersion.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                            latestVersionName = cfgLatestName.trim().ifBlank { "1.0.0" },
                            apkDownloadUrl = cfgApkUrl.trim(),
                            updateMessage = cfgUpdateMsg.trim(),
                            forceUpdate = cfgForceUpdate
                        )
                        isSavingConfig = true
                        scope.launch {
                            val res = repo.adminUpdateAppConfig(nextCfg)
                            isSavingConfig = false
                            showActionFeedback(res.message, !res.ok)
                            if (res.ok) {
                                appConfig = res.appConfig ?: nextCfg
                                showConfigDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF123B70))
                ) {
                    Text(if (isSavingConfig) "Saving..." else "Save Config")
                }
            },
            dismissButton = {
                TextButton(enabled = !isSavingConfig, onClick = { showConfigDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun StatSummaryCard(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        modifier = modifier.border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                text = title,
                fontSize = 10.sp,
                color = Color(0xFF475569),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SessionBadge(
    text: String,
    active: Boolean
) {
    val bg = if (active) Color(0xFFEFF6FF) else Color(0xFFF1F5F9)
    val borderColor = if (active) Color(0xFFBFDBFE) else Color(0xFFE2E8F0)
    val fg = if (active) Color(0xFF1D4ED8) else Color(0xFF64748B)
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = fg
        )
    }
}
