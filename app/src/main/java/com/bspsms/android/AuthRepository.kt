package com.bspsms.android

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

class AuthRepository(context: Context) {
    companion object {
        const val ADMIN_ID = "9679843774"
        const val ADMIN_PASS = "9933115114"
        const val APPS_SCRIPT_URL =
            "https://script.google.com/macros/s/AKfycbxK3DKwsX5S6NFSSurkfHQRzOrWL6I5NEiC9B9Qlty4cX5U1ycdosVychR-QwlKh9c/exec"
        private const val PREFS_NAME = "bsp_sms_auth_prefs"
        private const val KEY_IS_ADMIN = "session_is_admin"
        private const val KEY_USER_ID = "session_user_id"
        private const val KEY_PHONE = "session_phone"
        private const val KEY_EXPIRY = "session_expiry"
        private const val KEY_DEVICE_ID = "session_device_id"
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val KEY_FULL_NAME = "session_full_name"
        private const val KEY_USERNAME = "session_username"
        private const val KEY_ROLE = "session_role"
        private const val KEY_TOOL_PERMISSIONS = "session_tool_permissions"
        private const val KEY_APP_CONFIG = "cached_app_config"
        private const val KEY_LAST_PORTAL_OWNER = "last_portal_owner_key"
        private const val KEY_PORTAL_RESET_PREFIX = "ack_portal_reset_v_"
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getEffectiveScriptUrl(): String = APPS_SCRIPT_URL

    fun getLastPortalOwner(): String? {
        return prefs.getString(KEY_LAST_PORTAL_OWNER, null)?.takeIf { it.isNotBlank() }
    }

    fun setLastPortalOwner(ownerKey: String) {
        prefs.edit().putString(KEY_LAST_PORTAL_OWNER, ownerKey).commit()
    }

    fun getAckPortalResetVersion(ownerKey: String): Int {
        return prefs.getInt("$KEY_PORTAL_RESET_PREFIX$ownerKey", -1)
    }

    fun setAckPortalResetVersion(ownerKey: String, version: Int) {
        prefs.edit().putInt("$KEY_PORTAL_RESET_PREFIX$ownerKey", version).commit()
    }

    fun loadSavedSession(): AuthSession? {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val isAdmin = prefs.getBoolean(KEY_IS_ADMIN, false)
        val phone = prefs.getString(KEY_PHONE, "") ?: ""
        val expiry = prefs.getString(KEY_EXPIRY, "") ?: ""
        val deviceId = prefs.getString(KEY_DEVICE_ID, "") ?: resolveStableDeviceId(appContext)
        val token = prefs.getString(KEY_SESSION_TOKEN, "") ?: ""
        val fullName = prefs.getString(KEY_FULL_NAME, "") ?: ""
        val username = prefs.getString(KEY_USERNAME, "") ?: ""
        val role = prefs.getString(KEY_ROLE, if (isAdmin) "admin" else "general")
            ?: if (isAdmin) "admin" else "general"
        val permsJson = prefs.getString(KEY_TOOL_PERMISSIONS, null)
        val toolPermissions = if (!permsJson.isNullOrBlank()) {
            runCatching { parseToolPermissions(JSONObject(permsJson)) }.getOrDefault(ToolPermissions())
        } else {
            ToolPermissions()
        }
        return AuthSession(
            isAdmin = isAdmin,
            userId = userId,
            phone = phone,
            expiryDate = expiry,
            deviceId = deviceId,
            sessionToken = token,
            fullName = fullName,
            username = username,
            role = role,
            toolPermissions = toolPermissions
        )
    }

    fun saveSession(session: AuthSession) {
        prefs.edit()
            .putBoolean(KEY_IS_ADMIN, session.isAdmin)
            .putString(KEY_USER_ID, session.userId.trim())
            .putString(KEY_PHONE, session.phone.trim())
            .putString(KEY_EXPIRY, session.expiryDate.trim())
            .putString(KEY_DEVICE_ID, session.deviceId.trim())
            .putString(KEY_SESSION_TOKEN, session.sessionToken.trim())
            .putString(KEY_FULL_NAME, session.fullName.trim())
            .putString(KEY_USERNAME, session.username.trim())
            .putString(KEY_ROLE, session.role.trim().ifBlank { if (session.isAdmin) "admin" else "general" })
            .putString(KEY_TOOL_PERMISSIONS, toolPermissionsToJson(session.toolPermissions).toString())
            .apply()
    }

    fun loadSavedAppConfig(): AppConfigData {
        val raw = prefs.getString(KEY_APP_CONFIG, null)
        if (!raw.isNullOrBlank()) {
            runCatching { return parseAppConfig(JSONObject(raw)) }
        }
        return AppConfigData()
    }

    fun saveAppConfig(config: AppConfigData) {
        val json = JSONObject()
            .put("portal_url", config.portalUrl)
            .put("minimum_version_code", config.minimumVersionCode)
            .put("latest_version_name", config.latestVersionName)
            .put("apk_download_url", config.apkDownloadUrl)
            .put("update_message", config.updateMessage)
            .put("force_update", config.forceUpdate)
        prefs.edit().putString(KEY_APP_CONFIG, json.toString()).apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_IS_ADMIN)
            .remove(KEY_USER_ID)
            .remove(KEY_PHONE)
            .remove(KEY_EXPIRY)
            .remove(KEY_DEVICE_ID)
            .remove(KEY_SESSION_TOKEN)
            .remove(KEY_FULL_NAME)
            .remove(KEY_USERNAME)
            .remove(KEY_ROLE)
            .remove(KEY_TOOL_PERMISSIONS)
            .apply()
    }

    suspend fun register(
        userId: String,
        phone: String,
        password: String,
        deviceId: String,
        fullName: String = "",
        username: String = ""
    ): AuthResponse {
        val cleanId = normalizeUserId(userId)
        val cleanPhone = normalizePhoneNumber(phone)
        val cleanPass = normalizePassword(password)
        val cleanDevice = deviceId.trim()
        val payload = JSONObject()
            .put("action", "register")
            .put("userId", cleanId)
            .put("user_id", cleanId)
            .put("phone", cleanPhone)
            .put("phone_number", cleanPhone)
            .put("password", cleanPass)
            .put("deviceId", cleanDevice)
            .put("device_id", cleanDevice)
        if (fullName.isNotBlank()) {
            payload.put("fullName", fullName.trim())
        }
        if (username.isNotBlank()) {
            payload.put("username", username.trim())
        }
        return postWithGetFallback(payload)
    }

    suspend fun login(
        identifier: String,
        password: String,
        deviceId: String
    ): AuthResponse {
        val cleanIdentifier = normalizeLoginIdentifier(identifier)
        val cleanPass = normalizePassword(password)
        val cleanDevice = deviceId.trim()
        val isPhoneLogin = cleanIdentifier.all { it.isDigit() } && cleanIdentifier.length >= 10
        val payload = JSONObject()
            .put("action", "login")
            .put("identifier", cleanIdentifier)
            .put("password", cleanPass)
            .put("pass", cleanPass)
            .put("deviceId", cleanDevice)
            .put("device_id", cleanDevice)
        if (isPhoneLogin) {
            payload.put("phone", cleanIdentifier)
            payload.put("phone_number", cleanIdentifier)
            payload.put("mobile", cleanIdentifier)
        } else {
            payload.put("userId", cleanIdentifier.uppercase())
            payload.put("user_id", cleanIdentifier.uppercase())
        }
        return postWithGetFallback(payload)
    }

    suspend fun logout(session: AuthSession) {
        if (session.isAdmin) return
        val payload = JSONObject()
            .put("action", "logout")
            .put("userId", normalizeUserId(session.userId))
            .put("user_id", normalizeUserId(session.userId))
            .put("deviceId", session.deviceId.trim())
            .put("device_id", session.deviceId.trim())
            .put("sessionToken", session.sessionToken.trim())
        try {
            postWithGetFallback(payload)
        } catch (_: Exception) {
        }
    }

    suspend fun checkSession(userId: String, deviceId: String, sessionToken: String = ""): AuthResponse {
        val cleanId = normalizeUserId(userId)
        val cleanDevice = deviceId.trim()
        val payload = JSONObject()
            .put("action", "check_session")
            .put("userId", cleanId)
            .put("user_id", cleanId)
            .put("identifier", cleanId)
            .put("deviceId", cleanDevice)
            .put("device_id", cleanDevice)
            .put("sessionToken", sessionToken.trim())
            .put("session_token", sessionToken.trim())
        return postWithGetFallback(payload, fallbackActions = listOf("verify_status"))
    }

    suspend fun updateActivity(userId: String, deviceId: String, activity: String, toolKey: String = "") {
        val cleanId = normalizeUserId(userId)
        val cleanDevice = deviceId.trim()
        val payload = JSONObject()
            .put("action", "update_activity")
            .put("userId", cleanId)
            .put("user_id", cleanId)
            .put("deviceId", cleanDevice)
            .put("device_id", cleanDevice)
            .put("activity", activity.trim())
        if (toolKey.isNotBlank()) {
            payload.put("tool", toolKey.trim())
        }
        try {
            postToAppsScript(payload)
        } catch (_: Exception) {
        }
    }

    suspend fun verifyToolAccess(
        userId: String,
        deviceId: String,
        sessionToken: String,
        toolKey: String
    ): AuthResponse {
        val cleanId = normalizeUserId(userId)
        val cleanDevice = deviceId.trim()
        val payload = JSONObject()
            .put("action", "verify_tool_access")
            .put("userId", cleanId)
            .put("user_id", cleanId)
            .put("identifier", cleanId)
            .put("deviceId", cleanDevice)
            .put("device_id", cleanDevice)
            .put("sessionToken", sessionToken.trim())
            .put("session_token", sessionToken.trim())
            .put("tool", toolKey.trim())
        return postWithGetFallback(payload, fallbackActions = listOf("check_tool_permission", "check_session"))
    }

    suspend fun fetchAppConfig(): AuthResponse {
        val payload = JSONObject().put("action", "get_app_config")
        val res = postWithGetFallback(payload, fallbackActions = listOf("app_config"))
        res.appConfig?.let { saveAppConfig(it) }
        return res
    }

    suspend fun reportPortalState(
        userId: String,
        deviceId: String,
        portalSessionActive: Boolean,
        activity: String = ""
    ) {
        val cleanId = normalizeUserId(userId)
        val cleanDevice = deviceId.trim()
        val payload = JSONObject()
            .put("action", "report_portal_state")
            .put("userId", cleanId)
            .put("user_id", cleanId)
            .put("deviceId", cleanDevice)
            .put("device_id", cleanDevice)
            .put("portalSessionActive", portalSessionActive)
            .put("portal_session_active", portalSessionActive)
        if (activity.isNotBlank()) {
            payload.put("activity", activity.trim())
        }
        try {
            postToAppsScript(payload)
        } catch (_: Exception) {
        }
    }

    fun verifyAdminCredentials(adminId: String, adminPassword: String): Boolean {
        return adminId.trim() == ADMIN_ID && adminPassword.trim() == ADMIN_PASS
    }

    suspend fun adminLoginBackend(adminId: String, adminPassword: String): AuthResponse {
        val payload = JSONObject()
            .put("action", "admin_login")
            .put("adminId", adminId.trim())
            .put("admin_id", adminId.trim())
            .put("adminPassword", adminPassword.trim())
            .put("admin_password", adminPassword.trim())
        return postWithGetFallback(payload)
    }

    private fun buildAdminTargetPayload(
        action: String,
        targetUserId: String,
        phoneNumber: String = ""
    ): JSONObject {
        val cleanTarget = normalizeLoginIdentifier(targetUserId)
        val cleanPhone = normalizePhoneNumber(phoneNumber).ifBlank { phoneNumber.trim() }
        return JSONObject()
            .put("action", action)
            .put("adminId", ADMIN_ID)
            .put("admin_id", ADMIN_ID)
            .put("adminPassword", ADMIN_PASS)
            .put("admin_password", ADMIN_PASS)
            .put("targetUserId", cleanTarget)
            .put("target_user_id", cleanTarget)
            .put("userId", cleanTarget)
            .put("user_id", cleanTarget)
            .put("identifier", cleanTarget.ifBlank { cleanPhone })
            .put("phone", cleanPhone)
            .put("phone_number", cleanPhone)
    }

    suspend fun adminListUsers(): AuthResponse {
        val payload = JSONObject()
            .put("action", "admin_list_users")
            .put("adminId", ADMIN_ID)
            .put("admin_id", ADMIN_ID)
            .put("adminPassword", ADMIN_PASS)
            .put("admin_password", ADMIN_PASS)
        return postWithGetFallback(
            payload,
            fallbackActions = listOf("list_users", "get_users", "list")
        )
    }

    suspend fun adminActivateUser(targetUserId: String, phoneNumber: String = ""): AuthResponse {
        val expiryDate = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).plusDays(30).toString()
        val payload = buildAdminTargetPayload("activate", targetUserId, phoneNumber)
            .put("status", "active")
            .put("expiry_date", expiryDate)
            .put("expiryDate", expiryDate)
            .put("active_device_id", "")
            .put("activeDeviceId", "")
        return postWithGetFallback(payload, fallbackActions = listOf("admin_activate_user"))
    }

    suspend fun adminDeactivateUser(targetUserId: String, phoneNumber: String = ""): AuthResponse {
        val payload = buildAdminTargetPayload("deactivate", targetUserId, phoneNumber)
            .put("status", "inactive")
        return postWithGetFallback(
            payload,
            fallbackActions = listOf("inactive", "admin_deactivate_user")
        )
    }

    suspend fun adminEditUser(
        targetUserId: String,
        phone: String,
        fullName: String = "",
        username: String = ""
    ): AuthResponse {
        val cleanPhone = normalizePhoneNumber(phone)
        val payload = buildAdminTargetPayload("admin_edit_user", targetUserId, cleanPhone)
            .put("phone", cleanPhone)
            .put("phone_number", cleanPhone)
        if (fullName.isNotBlank()) {
            payload.put("fullName", fullName.trim())
        }
        if (username.isNotBlank()) {
            payload.put("username", username.trim())
        }
        return postWithGetFallback(payload)
    }

    suspend fun adminResetPassword(targetUserId: String, newPassword: String): AuthResponse {
        val cleanPass = normalizePassword(newPassword)
        val payload = buildAdminTargetPayload("admin_reset_password", targetUserId)
            .put("newPassword", cleanPass)
            .put("new_password", cleanPass)
            .put("password", cleanPass)
        return postWithGetFallback(payload)
    }

    suspend fun adminDeleteUser(targetUserId: String): AuthResponse {
        return postWithGetFallback(buildAdminTargetPayload("admin_delete_user", targetUserId))
    }

    suspend fun adminResetDevice(targetUserId: String, phoneNumber: String = ""): AuthResponse {
        val payload = buildAdminTargetPayload("reset_device", targetUserId, phoneNumber)
            .put("active_device_id", "")
            .put("activeDeviceId", "")
        return postWithGetFallback(payload, fallbackActions = listOf("admin_reset_device"))
    }

    suspend fun adminForcePortalLogout(targetUserId: String): AuthResponse {
        return postWithGetFallback(buildAdminTargetPayload("admin_force_portal_logout", targetUserId))
    }

    suspend fun adminResetPortalSession(targetUserId: String): AuthResponse {
        return postWithGetFallback(buildAdminTargetPayload("admin_reset_portal_session", targetUserId))
    }

    suspend fun adminUpdateAppConfig(config: AppConfigData): AuthResponse {
        val cfgObj = JSONObject()
            .put("portal_url", config.portalUrl.trim())
            .put("minimum_version_code", config.minimumVersionCode)
            .put("latest_version_name", config.latestVersionName.trim())
            .put("apk_download_url", config.apkDownloadUrl.trim())
            .put("update_message", config.updateMessage.trim())
            .put("force_update", config.forceUpdate)
        val payload = JSONObject()
            .put("action", "admin_update_config")
            .put("adminId", ADMIN_ID)
            .put("admin_id", ADMIN_ID)
            .put("adminPassword", ADMIN_PASS)
            .put("admin_password", ADMIN_PASS)
            .put("appConfig", cfgObj)
            .put("portal_url", config.portalUrl.trim())
            .put("minimum_version_code", config.minimumVersionCode)
            .put("latest_version_name", config.latestVersionName.trim())
            .put("apk_download_url", config.apkDownloadUrl.trim())
            .put("update_message", config.updateMessage.trim())
            .put("force_update", config.forceUpdate)
        val res = postWithGetFallback(payload, fallbackActions = listOf("admin_update_app_config", "update_app_config"))
        if (res.ok && res.appConfig != null) {
            saveAppConfig(res.appConfig)
        }
        return res
    }

    suspend fun adminUpdateToolPermissions(
        targetUserId: String,
        permissions: ToolPermissions,
        phoneNumber: String = ""
    ): AuthResponse {
        val permsJson = toolPermissionsToJson(permissions)
        val payload = buildAdminTargetPayload("admin_update_tool_permissions", targetUserId, phoneNumber)
            .put("toolPermissions", permsJson)
            .put("tool_permissions", permsJson)
            .put("formative", permissions.formative)
            .put("talk", permissions.talk)
            .put("lpcd", permissions.lpcd)
            .put("bco", permissions.bco)
            .put("excel", permissions.excel)
            .put("marksheet", permissions.marksheet)
            .put("result_ready", permissions.resultReady)
            .put("hprc_scan", permissions.hprcScan)
        return postWithGetFallback(payload, fallbackActions = listOf("admin_set_tool_permissions", "set_tool_permissions"))
    }

    private fun isProtocolOrActionFailure(res: AuthResponse): Boolean {
        return res.code == "UNKNOWN_ACTION" ||
            res.code == "NETWORK_ERROR" ||
            res.code == "EMPTY_RESPONSE" ||
            res.code == "PARSE_ERROR" ||
            res.code.startsWith("HTTP_") ||
            res.message.contains("unsupported action", ignoreCase = true) ||
            res.message.contains("unknown action", ignoreCase = true) ||
            res.message.contains("invalid action", ignoreCase = true) ||
            res.message.contains("action not found", ignoreCase = true) ||
            res.message.contains("not supported", ignoreCase = true)
    }

    private suspend fun postWithGetFallback(
        payload: JSONObject,
        fallbackActions: List<String> = emptyList()
    ): AuthResponse {
        val primaryPost = postToAppsScript(payload)
        if (primaryPost.ok || !isProtocolOrActionFailure(primaryPost)) {
            return primaryPost
        }

        val primaryGet = getFromAppsScript(payload)
        if (primaryGet.ok || !isProtocolOrActionFailure(primaryGet)) {
            return primaryGet
        }

        var lastRes = primaryGet
        for (altAction in fallbackActions) {
            if (altAction.isBlank()) continue
            val aliasPayload = JSONObject(payload.toString()).put("action", altAction)
            val aliasPost = postToAppsScript(aliasPayload)
            if (aliasPost.ok || !isProtocolOrActionFailure(aliasPost)) {
                return aliasPost
            }
            val aliasGet = getFromAppsScript(aliasPayload)
            if (aliasGet.ok || !isProtocolOrActionFailure(aliasGet)) {
                return aliasGet
            }
            lastRes = aliasGet
        }

        return lastRes
    }

    private suspend fun getFromAppsScript(payload: JSONObject): AuthResponse = withContext(Dispatchers.IO) {
        val baseUrl = getEffectiveScriptUrl()
        val queryBuilder = StringBuilder()
        val iter = payload.keys()
        while (iter.hasNext()) {
            val key = iter.next()
            val value = payload.opt(key)?.toString() ?: ""
            if (queryBuilder.isNotEmpty()) queryBuilder.append('&')
            queryBuilder.append(URLEncoder.encode(key, "UTF-8"))
                .append('=')
                .append(URLEncoder.encode(value, "UTF-8"))
        }
        val sep = if (baseUrl.contains("?")) "&" else "?"
        var currentUrl = "$baseUrl$sep$queryBuilder"
        var redirectCount = 0

        while (redirectCount < 8) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 25000
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty("User-Agent", "BSP-SMS-Android/1.0")
            }
            try {
                val status = conn.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == HttpURLConnection.HTTP_SEE_OTHER ||
                    status == 307 || status == 308
                ) {
                    val nextLocation = conn.getHeaderField("Location")
                    runCatching { conn.inputStream?.close() }
                    runCatching { conn.errorStream?.close() }
                    if (nextLocation.isNullOrBlank()) {
                        return@withContext AuthResponse(
                            ok = false,
                            code = "NETWORK_ERROR",
                            message = "Redirect missing Location header (HTTP $status)."
                        )
                    }
                    currentUrl = URL(URL(currentUrl), nextLocation).toString()
                    redirectCount++
                    continue
                }
                val stream = (if (status in 200..299) conn.inputStream else conn.errorStream)
                    ?: conn.inputStream
                val responseText = if (stream != null) {
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
                } else {
                    ""
                }
                return@withContext parseResponse(responseText, status)
            } catch (e: Exception) {
                return@withContext AuthResponse(
                    ok = false,
                    code = "NETWORK_ERROR",
                    message = "Connection failed: ${e.message ?: "Check internet connection"}"
                )
            } finally {
                conn.disconnect()
            }
        }

        AuthResponse(
            ok = false,
            code = "NETWORK_ERROR",
            message = "Too many redirects from Google Apps Script."
        )
    }

    private suspend fun postToAppsScript(payload: JSONObject): AuthResponse = withContext(Dispatchers.IO) {
        val initialUrl = getEffectiveScriptUrl()
        val bodyStr = payload.toString()
        val bodyBytes = bodyStr.toByteArray(Charsets.UTF_8)

        var currentUrl = initialUrl
        var method = "POST"
        var redirectCount = 0

        while (redirectCount < 8) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = method
                connectTimeout = 15000
                readTimeout = 25000
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty("User-Agent", "BSP-SMS-Android/1.0")
                if (method == "POST") {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setFixedLengthStreamingMode(bodyBytes.size)
                }
            }

            try {
                if (method == "POST") {
                    conn.outputStream.use { out ->
                        out.write(bodyBytes)
                        out.flush()
                    }
                }

                val status = conn.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == HttpURLConnection.HTTP_SEE_OTHER ||
                    status == 307 || status == 308
                ) {
                    val nextLocation = conn.getHeaderField("Location")
                    runCatching { conn.inputStream?.close() }
                    runCatching { conn.errorStream?.close() }
                    if (nextLocation.isNullOrBlank()) {
                        return@withContext AuthResponse(
                            ok = false,
                            code = "NETWORK_ERROR",
                            message = "Redirect missing Location header (HTTP $status)."
                        )
                    }
                    val resolvedUrl = URL(URL(currentUrl), nextLocation).toString()
                    currentUrl = resolvedUrl
                    // Google Apps Script executes doPost() on /exec and issues a redirect to
                    // script.googleusercontent.com/macros/echo?... which only accepts GET.
                    if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                        status == HttpURLConnection.HTTP_MOVED_PERM ||
                        status == HttpURLConnection.HTTP_SEE_OTHER ||
                        resolvedUrl.contains("googleusercontent.com", ignoreCase = true) ||
                        resolvedUrl.contains("macros/echo", ignoreCase = true)
                    ) {
                        method = "GET"
                    }
                    redirectCount++
                    continue
                }

                val stream = (if (status in 200..299) conn.inputStream else conn.errorStream)
                    ?: conn.inputStream
                val responseText = if (stream != null) {
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
                } else {
                    ""
                }
                return@withContext parseResponse(responseText, status)
            } catch (e: Exception) {
                return@withContext AuthResponse(
                    ok = false,
                    code = "NETWORK_ERROR",
                    message = "Connection failed: ${e.message ?: "Check internet connection"}"
                )
            } finally {
                conn.disconnect()
            }
        }

        AuthResponse(
            ok = false,
            code = "NETWORK_ERROR",
            message = "Too many redirects from Google Apps Script."
        )
    }

    private fun parseResponse(rawText: String, httpStatus: Int): AuthResponse {
        var trimmed = rawText.trim().removePrefix("\uFEFF").trim()
        if (trimmed.startsWith(")]}'")) {
            trimmed = trimmed.substringAfter('\n', "").trim()
        }
        if (trimmed.isEmpty()) {
            return AuthResponse(
                ok = false,
                code = if (httpStatus !in 200..299) "HTTP_$httpStatus" else "EMPTY_RESPONSE",
                message = "Empty response from Google Apps Script (HTTP $httpStatus)."
            )
        }

        val isHtmlResponse = trimmed.startsWith("<") ||
            trimmed.contains("<!doctype html", ignoreCase = true) ||
            trimmed.contains("<html", ignoreCase = true)

        if (!isHtmlResponse) {
            try {
                val tokenerValue = JSONTokener(trimmed).nextValue()
                when (tokenerValue) {
                    is JSONArray -> {
                        val users = parseUsers(tokenerValue)
                        return AuthResponse(
                            ok = httpStatus in 200..299,
                            code = if (httpStatus in 200..299) "OK" else "HTTP_$httpStatus",
                            message = "Loaded ${users.size} user(s).",
                            users = users
                        )
                    }
                    is JSONObject -> return parseObjectResponse(tokenerValue, httpStatus)
                }
            } catch (_: Exception) {
                // Fall through to HTML/error handling below
            }
        }

        val snippet = trimmed
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
            .replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(220)

        val friendlyMessage = when {
            httpStatus == 404 || snippet.contains("Error 404", ignoreCase = true) || snippet.contains("Page Not Found", ignoreCase = true) ->
                "Google Apps Script endpoint returned HTTP 404. Verify the Web App deployment is active."
            snippet.contains("Sign in", ignoreCase = true) || snippet.contains("accounts.google.com", ignoreCase = true) ->
                "Google Apps Script requires 'Who has access: Anyone' in Web App deployment settings."
            snippet.isNotBlank() ->
                "Server response (HTTP $httpStatus): $snippet"
            else ->
                "Unexpected server response (HTTP $httpStatus). Ensure Apps Script is deployed as Web App with access 'Anyone'."
        }

        return AuthResponse(
            ok = false,
            code = if (httpStatus !in 200..299) "HTTP_$httpStatus" else "PARSE_ERROR",
            message = friendlyMessage
        )
    }

    private fun parseObjectResponse(json: JSONObject, httpStatus: Int): AuthResponse {
        val usersArr = findUsersArray(json)
        val usersList = if (usersArr != null) parseUsers(usersArr) else emptyList()

        val userObj = findSingleUserObject(json)
        val parsedUser = userObj?.let(::parseUser)

        val statsObj = optObjectCaseInsensitive(json, "stats")
        val derivedTotal = usersList.size
        val derivedActive = usersList.count { it.isActive && !it.isExpired }
        val derivedInactive = usersList.count { !it.isActive }
        val derivedExpired = usersList.count { it.isActive && it.isExpired }
        val derivedOnline = usersList.count { it.appLoggedIn && it.isActive && !it.isExpired }
        val derivedPortal = usersList.count { it.portalSessionActive && it.isActive && !it.isExpired }

        val parsedStats = statsObj?.let { s ->
            AdminStats(
                totalGeneralUsers = firstIntOrDefault(s, derivedTotal, "totalGeneralUsers", "total_general_users", "totalUsers", "total_users", "total"),
                activeUsers = firstIntOrDefault(s, derivedActive, "activeUsers", "active_users", "active"),
                inactiveUsers = firstIntOrDefault(s, derivedInactive, "inactiveUsers", "inactive_users", "inactive"),
                expiredUsers = firstIntOrDefault(s, derivedExpired, "expiredUsers", "expired_users", "expired"),
                loggedInAppUsers = firstIntOrDefault(s, derivedOnline, "loggedInAppUsers", "logged_in_app_users", "loggedInUsers", "logged_in_users", "loggedIn", "logged_in"),
                activePortalUsers = firstIntOrDefault(s, derivedPortal, "activePortalUsers", "active_portal_users")
            )
        }

        val rawStatus = firstNonBlank(json, "status", "state").lowercase()
        val rawErrorVal = firstNonBlank(json, "error", "errorMessage", "error_message")
        val hasMeaningfulError = rawErrorVal.isNotBlank() &&
            !rawErrorVal.equals("false", ignoreCase = true) &&
            !rawErrorVal.equals("none", ignoreCase = true) &&
            rawErrorVal != "0"
        val serverMessage = firstNonBlank(
            json,
            "message", "msg",
            *(if (hasMeaningfulError) arrayOf("error", "errorMessage", "error_message") else emptyArray()),
            "reason", "detail"
        )
        val sessionToken = firstNonBlank(json, "sessionToken", "session_token", "token")

        val hasExplicitFalse =
            (json.has("ok") && !json.optBoolean("ok", true)) ||
                (json.has("success") && !json.optBoolean("success", true)) ||
                hasMeaningfulError ||
                rawStatus in setOf(
                    "error", "fail", "failed", "false",
                    "inactive", "expired", "locked", "unauthorized", "denied"
                )

        val hasExplicitSuccess =
            (json.has("ok") && json.optBoolean("ok", false)) ||
                (json.has("success") && json.optBoolean("success", false)) ||
                rawStatus in setOf("success", "ok", "active") ||
                serverMessage.contains("login successful", ignoreCase = true)

        val isOk = when {
            hasExplicitFalse -> false
            hasExplicitSuccess -> true
            usersArr != null || parsedUser != null -> httpStatus in 200..299
            else -> httpStatus in 200..299
        }

        val code = firstNonBlank(json, "code", "errorCode", "error_code")
            .ifBlank { if (isOk) "OK" else "SERVER_ERROR" }

        val resolvedMessage = when {
            serverMessage.isNotBlank() -> serverMessage
            usersArr != null && isOk -> "Loaded ${usersList.size} user(s)."
            isOk -> "Done"
            else -> "Request failed (HTTP $httpStatus, $code)."
        }

        val permsObj = optObjectCaseInsensitive(json, "toolPermissions")
            ?: optObjectCaseInsensitive(json, "tool_permissions")
            ?: userObj?.let { optObjectCaseInsensitive(it, "toolPermissions") ?: optObjectCaseInsensitive(it, "tool_permissions") }
        val parsedPerms = permsObj?.let(::parseToolPermissions) ?: parsedUser?.toolPermissions

        val cfgObj = optObjectCaseInsensitive(json, "appConfig")
            ?: optObjectCaseInsensitive(json, "app_config")
        val parsedConfig = cfgObj?.let(::parseAppConfig)?.also { saveAppConfig(it) }

        return AuthResponse(
            ok = isOk,
            code = code,
            message = resolvedMessage,
            sessionToken = sessionToken,
            user = parsedUser,
            users = usersList,
            stats = parsedStats,
            toolPermissions = parsedPerms,
            appConfig = parsedConfig
        )
    }

    private fun firstIntOrDefault(obj: JSONObject, fallback: Int, vararg keys: String): Int {
        for (k in keys) {
            if (obj.has(k) && !obj.isNull(k)) {
                val parsed = obj.opt(k)?.toString()?.trim()?.toIntOrNull()
                if (parsed != null) return parsed
            }
        }
        return fallback
    }

    private fun optArrayCaseInsensitive(obj: JSONObject, key: String): JSONArray? {
        obj.optJSONArray(key)?.let { return it }
        val iter = obj.keys()
        while (iter.hasNext()) {
            val actual = iter.next()
            if (actual.equals(key, ignoreCase = true)) {
                obj.optJSONArray(actual)?.let { return it }
            }
        }
        return null
    }

    private fun optObjectCaseInsensitive(obj: JSONObject, key: String): JSONObject? {
        obj.optJSONObject(key)?.let { return it }
        val iter = obj.keys()
        while (iter.hasNext()) {
            val actual = iter.next()
            if (actual.equals(key, ignoreCase = true)) {
                obj.optJSONObject(actual)?.let { return it }
            }
        }
        return null
    }

    private fun findUsersArray(json: JSONObject): JSONArray? {
        val arrayKeys = listOf("users", "allUsers", "all_users", "data", "rows", "items", "list", "result", "records")
        for (key in arrayKeys) {
            optArrayCaseInsensitive(json, key)?.let { return it }
        }
        val nestedObj = optObjectCaseInsensitive(json, "data") ?: optObjectCaseInsensitive(json, "result")
        if (nestedObj != null) {
            for (key in arrayKeys) {
                optArrayCaseInsensitive(nestedObj, key)?.let { return it }
            }
        }
        return null
    }

    private fun findSingleUserObject(json: JSONObject): JSONObject? {
        val objectKeys = listOf("user", "data", "row", "item", "record")
        for (key in objectKeys) {
            optObjectCaseInsensitive(json, key)?.let { candidate ->
                if (looksLikeUserObject(candidate)) return candidate
            }
        }
        return if (looksLikeUserObject(json)) json else null
    }

    private fun looksLikeUserObject(obj: JSONObject): Boolean {
        val identityKeys = setOf("userid", "phone", "phonenumber", "mobile", "username", "expirydate", "activedeviceid")
        val iter = obj.keys()
        while (iter.hasNext()) {
            val norm = iter.next().lowercase().replace(Regex("[^a-z0-9]"), "")
            if (norm in identityKeys) return true
        }
        return false
    }

    private fun parseBooleanField(obj: JSONObject, vararg keys: String): Boolean {
        val raw = firstNonBlank(obj, *keys).lowercase()
        return raw == "true" || raw == "1" || raw == "yes" || raw == "active"
    }

    private fun parseIntField(obj: JSONObject, vararg keys: String): Int {
        val raw = firstNonBlank(obj, *keys)
        return raw.toIntOrNull() ?: 0
    }

    private fun parseUser(obj: JSONObject): UserRecord {
        val rawUserId = firstNonBlank(obj, "userId", "user_id", "id", "userid", "UID", "uid")
        val rawPhone = firstNonBlank(obj, "phone", "phone_number", "phoneNumber", "mobile", "mobile_number", "contact")
        val rawFullName = firstNonBlank(obj, "fullName", "full_name", "name", "displayName", "display_name")
        val rawUsername = firstNonBlank(obj, "username", "user_name", "loginName", "login_name")
        val rawRole = firstNonBlank(obj, "role", "user_role", "account_role").ifBlank { "general" }
        val rawStatusExplicit = firstNonBlank(obj, "status", "state", "account_status")
        val rawStatus = rawStatusExplicit.ifBlank { "inactive" }
        val rawExpiryExplicit = firstNonBlank(obj, "expiryDate", "expiry_date", "expiry", "valid_until", "validUntil", "expires_at")
        val rawDevice = firstNonBlank(obj, "activeDeviceId", "active_device_id", "device_id", "deviceId", "device")
        val rawLastLogin = firstNonBlank(obj, "lastLogin", "last_login", "lastLoginAt", "login_time")
        val rawLastActivity = firstNonBlank(obj, "lastActivity", "last_activity", "activity", "updated_at")
        val rawCreatedAt = firstNonBlank(obj, "createdAt", "created_at", "registered_at")
        val rawPortalUpdated = firstNonBlank(obj, "portalLastUpdated", "portal_last_updated", "portalUpdatedAt")
        val rawAppLoggedIn = firstNonBlank(obj, "appLoggedIn", "app_logged_in", "is_logged_in", "logged_in")
        val normalizedStatus = rawStatus.trim().lowercase()
        val normalizedExpiry = normalizeExpiryDateString(rawExpiryExplicit)
        val normalizedDevice = rawDevice.trim()
        val computedAppLoggedIn = if (rawAppLoggedIn.isNotBlank()) {
            parseBooleanField(obj, "appLoggedIn", "app_logged_in", "is_logged_in", "logged_in")
        } else {
            normalizedDevice.isNotBlank() &&
                normalizedStatus in setOf("active", "success", "ok") &&
                !isExpiryDatePassed(normalizedExpiry)
        }

        val permsObj = optObjectCaseInsensitive(obj, "toolPermissions")
            ?: optObjectCaseInsensitive(obj, "tool_permissions")
        val parsedPerms = permsObj?.let(::parseToolPermissions) ?: ToolPermissions()

        return UserRecord(
            userId = normalizeUserId(rawUserId),
            phone = normalizePhoneNumber(rawPhone).ifBlank { rawPhone.trim() },
            fullName = rawFullName.trim(),
            username = rawUsername.trim(),
            role = rawRole.trim().lowercase(),
            status = normalizedStatus,
            expiryDate = normalizedExpiry,
            activeDeviceId = normalizedDevice,
            lastLogin = rawLastLogin.trim(),
            lastActivity = rawLastActivity.trim(),
            createdAt = rawCreatedAt.trim(),
            appLoggedIn = computedAppLoggedIn,
            portalSessionActive = parseBooleanField(obj, "portalSessionActive", "portal_session_active", "portal_active"),
            portalResetVersion = parseIntField(obj, "portalResetVersion", "portal_reset_version", "portal_reset"),
            portalLastUpdated = rawPortalUpdated.trim(),
            hasStatusField = rawStatusExplicit.isNotBlank(),
            hasExpiryField = rawExpiryExplicit.isNotBlank(),
            hasAppLoggedInField = rawAppLoggedIn.isNotBlank(),
            toolPermissions = parsedPerms
        )
    }

    private fun toolPermissionsToJson(perms: ToolPermissions): JSONObject {
        return JSONObject()
            .put("formative", perms.formative)
            .put("talk", perms.talk)
            .put("lpcd", perms.lpcd)
            .put("bco", perms.bco)
            .put("excel", perms.excel)
            .put("marksheet", perms.marksheet)
            .put("result_ready", perms.resultReady)
            .put("hprc_scan", perms.hprcScan)
    }

    private fun parseToolPermissions(obj: JSONObject): ToolPermissions {
        fun permFlag(vararg keys: String): Boolean {
            val raw = firstNonBlank(obj, *keys).lowercase()
            if (raw.isEmpty()) return true
            return raw !in setOf("false", "0", "no", "off", "disabled", "blocked", "deny")
        }
        return ToolPermissions(
            formative = permFlag("formative", "formative_filler"),
            talk = permFlag("talk", "talk_to_filler"),
            lpcd = permFlag("lpcd", "lpcd_filler"),
            bco = permFlag("bco", "bco_filler"),
            excel = permFlag("excel", "excel_filler"),
            marksheet = permFlag("marksheet", "bulk_marksheet", "bulk_marksheet_downloader"),
            resultReady = permFlag("result_ready", "result", "resultReady"),
            hprcScan = permFlag("hprc_scan", "hprc", "hprcScan")
        )
    }

    private fun parseAppConfig(obj: JSONObject): AppConfigData {
        val rawUrl = firstNonBlank(obj, "portal_url", "portalUrl")
            .takeIf { it.startsWith("http", ignoreCase = true) }
            ?: AppConfigData().portalUrl
        val minCode = firstIntOrDefault(obj, 1, "minimum_version_code", "minimumVersionCode", "min_version_code").coerceAtLeast(1)
        val latestName = firstNonBlank(obj, "latest_version_name", "latestVersionName", "version_name").ifBlank { "1.0.0" }
        val apkUrl = firstNonBlank(obj, "apk_download_url", "apkDownloadUrl", "download_url")
        val msg = firstNonBlank(obj, "update_message", "updateMessage")
        val force = parseBooleanField(obj, "force_update", "forceUpdate")
        return AppConfigData(
            portalUrl = rawUrl,
            minimumVersionCode = minCode,
            latestVersionName = latestName,
            apkDownloadUrl = apkUrl,
            updateMessage = msg,
            forceUpdate = force
        )
    }

    private fun parseUsers(arr: JSONArray): List<UserRecord> {
        val list = mutableListOf<UserRecord>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i)
            if (obj != null) {
                list.add(parseUser(obj))
                continue
            }
            // Fallback if a custom sheet endpoint returns raw A:G row arrays:
            // [user_id, phone_number, password, status, expiry_date, active_device_id, last_login]
            val rowArr = arr.optJSONArray(i)
            if (rowArr != null && rowArr.length() >= 4) {
                val col0 = rowArr.opt(0)?.toString()?.trim().orEmpty()
                if (col0.equals("user_id", ignoreCase = true) || col0.equals("userid", ignoreCase = true)) {
                    continue
                }
                val synthetic = JSONObject()
                    .put("user_id", col0)
                    .put("phone_number", rowArr.opt(1)?.toString()?.trim().orEmpty())
                    .put("status", rowArr.opt(3)?.toString()?.trim().orEmpty())
                    .put("expiry_date", if (rowArr.length() > 4) rowArr.opt(4)?.toString()?.trim().orEmpty() else "")
                    .put("active_device_id", if (rowArr.length() > 5) rowArr.opt(5)?.toString()?.trim().orEmpty() else "")
                    .put("last_login", if (rowArr.length() > 6) rowArr.opt(6)?.toString()?.trim().orEmpty() else "")
                if (synthetic.optString("user_id").isNotBlank() || synthetic.optString("phone_number").isNotBlank()) {
                    list.add(parseUser(synthetic))
                }
            }
        }
        return list
    }

    private fun firstNonBlank(obj: JSONObject, vararg keys: String): String {
        for (k in keys) {
            if (obj.has(k) && !obj.isNull(k)) {
                val v = obj.opt(k)?.toString()?.trim().orEmpty()
                if (v.isNotEmpty() && !v.equals("null", ignoreCase = true)) {
                    return v
                }
            }
        }
        val available = mutableMapOf<String, String>()
        val iter = obj.keys()
        while (iter.hasNext()) {
            val actual = iter.next()
            val normalized = actual.lowercase().replace(Regex("[^a-z0-9]"), "")
            available[actual.lowercase()] = actual
            if (normalized.isNotEmpty() && !available.containsKey(normalized)) {
                available[normalized] = actual
            }
        }
        for (k in keys) {
            val matched = available[k.lowercase()]
                ?: available[k.lowercase().replace(Regex("[^a-z0-9]"), "")]
            if (matched != null && !obj.isNull(matched)) {
                val v = obj.opt(matched)?.toString()?.trim().orEmpty()
                if (v.isNotEmpty() && !v.equals("null", ignoreCase = true)) {
                    return v
                }
            }
        }
        return ""
    }
}
