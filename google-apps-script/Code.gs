/**
 * BSP-SMS Serverless Auth, 30-Day Activation, Single-Device Lock & Admin Control
 * Compatible with exact Google Sheet columns A to G:
 *   A: user_id
 *   B: phone_number
 *   C: password
 *   D: status
 *   E: expiry_date
 *   F: active_device_id
 *   G: last_login
 *
 * Deploy as Web App:
 *   Execute as: Me
 *   Who has access: Anyone
 */

const SHEET_NAME = "Users";
const CONFIG_SHEET_NAME = "AppConfig";
const TOOL_PERMISSIONS_SHEET_NAME = "ToolPermissions";
const TIMEZONE = "Asia/Kolkata";
const ADMIN_ID = "9679843774";
const ADMIN_PASSWORD = "9933115114";

const APP_CONFIG_HEADERS = [
  "portal_url",
  "minimum_version_code",
  "latest_version_name",
  "apk_download_url",
  "update_message",
  "force_update",
  "updated_at"
];

const DEFAULT_APP_CONFIG = {
  portal_url: "https://school.banglarshiksha.gov.in/sms/",
  minimum_version_code: 1,
  latest_version_name: "1.0.0",
  apk_download_url: "",
  update_message: "",
  force_update: false
};

const TOOL_KEYS = [
  "formative",
  "talk",
  "lpcd",
  "bco",
  "excel",
  "marksheet",
  "result_ready",
  "hprc_scan"
];

const TOOL_PERMISSION_HEADERS = [
  "user_id",
  "formative",
  "talk",
  "lpcd",
  "bco",
  "excel",
  "marksheet",
  "result_ready",
  "hprc_scan",
  "updated_at",
  "updated_by"
];

const HEADERS = [
  "user_id",
  "phone_number",
  "password",
  "status",
  "expiry_date",
  "active_device_id",
  "last_login"
];

function doGet(e) {
  if (e && e.parameter && e.parameter.action) {
    return executeWithLock(e.parameter);
  }
  return jsonOut({ ok: true, status: "success", message: "BSP-SMS Auth Service Ready" });
}

function doPost(e) {
  let body = {};
  try {
    const raw = (e && e.postData && e.postData.contents) ? e.postData.contents : "";
    if (raw) {
      body = JSON.parse(raw);
    }
  } catch (parseErr) {
    body = {};
  }
  if (e && e.parameter) {
    const paramKeys = Object.keys(e.parameter);
    for (let i = 0; i < paramKeys.length; i++) {
      const k = paramKeys[i];
      if (body[k] === undefined || body[k] === "") {
        body[k] = e.parameter[k];
      }
    }
  }
  return executeWithLock(body);
}

function executeWithLock(body) {
  const lock = LockService.getScriptLock();
  lock.waitLock(15000);
  try {
    const action = String((body && body.action) || "").trim().toLowerCase();

    switch (action) {
      case "register":
        return jsonOut(handleRegister(body));
      case "login":
        return jsonOut(handleLogin(body));
      case "logout":
        return jsonOut(handleLogout(body));
      case "check_session":
      case "verify_status":
        return jsonOut(handleCheckSession(body));
      case "update_activity":
        return jsonOut(handleUpdateActivity(body));
      case "verify_tool_access":
      case "check_tool_permission":
        return jsonOut(handleVerifyToolAccess(body));
      case "get_app_config":
      case "app_config":
        return jsonOut(handleGetAppConfig());
      case "report_portal_state":
        return jsonOut(handleReportPortalState(body));
      case "admin_login":
        return jsonOut(handleAdminLogin(body));
      case "admin_list_users":
      case "list_users":
      case "list":
      case "get_users":
        return jsonOut(handleAdminListUsers(body));
      case "admin_activate_user":
      case "activate":
        return jsonOut(handleAdminActivateUser(body));
      case "admin_deactivate_user":
      case "deactivate":
      case "inactive":
        return jsonOut(handleAdminDeactivateUser(body));
      case "admin_edit_user":
        return jsonOut(handleAdminEditUser(body));
      case "admin_reset_password":
        return jsonOut(handleAdminResetPassword(body));
      case "admin_delete_user":
        return jsonOut(handleAdminDeleteUser(body));
      case "admin_reset_device":
      case "reset_device":
        return jsonOut(handleAdminResetDevice(body));
      case "admin_force_portal_logout":
      case "admin_reset_portal_session":
        return jsonOut(handleAdminResetPortalSession(body, action === "admin_force_portal_logout"));
      case "admin_get_config":
      case "admin_get_app_config":
        return jsonOut(handleAdminGetConfig(body));
      case "admin_update_config":
      case "admin_update_app_config":
      case "update_app_config":
        return jsonOut(handleAdminUpdateConfig(body));
      case "admin_set_tool_permissions":
      case "admin_update_tool_permissions":
      case "set_tool_permissions":
        return jsonOut(handleAdminUpdateToolPermissions(body));
      default:
        return jsonOut({ ok: false, status: "error", code: "UNKNOWN_ACTION", message: "Unsupported action: " + action });
    }
  } catch (err) {
    return jsonOut({ ok: false, status: "error", code: "SERVER_ERROR", message: String(err && err.message ? err.message : err) });
  } finally {
    lock.releaseLock();
  }
}

function normalizeHeaderKey(raw) {
  return String(raw || "").trim().toLowerCase().replace(/[^a-z0-9]/g, "");
}

function ensureHeaders(sheet) {
  const currentLastCol = sheet.getLastColumn();
  if (currentLastCol === 0) {
    sheet.appendRow(HEADERS);
    sheet.getRange(1, 1, 1, HEADERS.length).setFontWeight("bold");
    sheet.setFrozenRows(1);
  }
}

function getSheetSchema(sheet) {
  ensureHeaders(sheet);
  const lastCol = Math.max(sheet.getLastColumn(), HEADERS.length);
  const headerRow = sheet.getRange(1, 1, 1, lastCol).getValues()[0];
  const map = {};
  for (let i = 0; i < headerRow.length; i++) {
    const key = normalizeHeaderKey(headerRow[i]);
    if (key && map[key] === undefined) {
      map[key] = i;
    }
  }

  function findCol(aliases, fallbackIndex) {
    for (let i = 0; i < aliases.length; i++) {
      const norm = normalizeHeaderKey(aliases[i]);
      if (map[norm] !== undefined) return map[norm];
    }
    return fallbackIndex;
  }

  return {
    colCount: lastCol,
    userId: findCol(["user_id", "userid", "id", "uid"], 0),
    phone: findCol(["phone_number", "phonenumber", "phone", "mobile", "mobilenumber", "contact"], 1),
    password: findCol(["password", "pass", "pwd"], 2),
    status: findCol(["status", "state", "account_status"], 3),
    expiryDate: findCol(["expiry_date", "expirydate", "expiry", "valid_until", "validuntil", "expires_at"], 4),
    activeDeviceId: findCol(["active_device_id", "activedeviceid", "device_id", "deviceid", "device"], 5),
    lastLogin: findCol(["last_login", "lastlogin", "lastloginat", "login_time"], 6),
    // Optional columns: -1 when absent in A:G sheets
    lastActivity: findCol(["last_activity", "lastactivity", "activity", "updated_at"], -1),
    createdAt: findCol(["created_at", "createdat", "registered_at"], -1),
    fullName: findCol(["full_name", "fullname", "name", "display_name"], -1),
    username: findCol(["username", "user_name", "login_name"], -1),
    role: findCol(["role", "user_role", "account_role"], -1),
    appLoggedIn: findCol(["app_logged_in", "apploggedin", "is_logged_in", "logged_in"], -1),
    sessionToken: findCol(["session_token", "sessiontoken", "token"], -1),
    portalSessionActive: findCol(["portal_session_active", "portalsessionactive", "portal_active"], -1),
    portalResetVersion: findCol(["portal_reset_version", "portalresetversion", "portal_reset"], -1),
    portalLastUpdated: findCol(["portal_last_updated", "portallastupdated", "portal_updated_at"], -1)
  };
}

function setCellIfPresent(sheet, rowIndex, colIndex, value, numberFormat) {
  if (colIndex < 0) return;
  const cell = sheet.getRange(rowIndex, colIndex + 1);
  if (numberFormat) {
    cell.setNumberFormat(numberFormat);
  }
  cell.setValue(value);
}

function onEdit(e) {
  if (!e || !e.range) return;
  const sheet = e.range.getSheet();
  if (sheet.getName() !== SHEET_NAME) return;
  const row = e.range.getRow();
  const col = e.range.getColumn();
  const schema = getSheetSchema(sheet);
  if (row <= 1 || col !== schema.status + 1) return;

  const newStatus = String(e.value || "").trim().toLowerCase();
  if (newStatus === "active") {
    const expiryCell = sheet.getRange(row, schema.expiryDate + 1);
    const currentExpiry = formatDateValue(expiryCell.getValue());
    if (!currentExpiry || todayStr() > currentExpiry) {
      expiryCell.setNumberFormat("@").setValue(addDaysStr(30));
    }
    sheet.getRange(row, schema.activeDeviceId + 1).setValue("");
    setCellIfPresent(sheet, row, schema.lastActivity, "Activated 30d & device unlocked in Sheet (" + nowStr() + ")");
  } else if (newStatus === "inactive") {
    setCellIfPresent(sheet, row, schema.appLoggedIn, "false");
    setCellIfPresent(sheet, row, schema.sessionToken, "");
    setCellIfPresent(sheet, row, schema.lastActivity, "Deactivated in Sheet (" + nowStr() + ")");
  }
}

function getUsersSheet() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = ss.getSheetByName(SHEET_NAME);
  if (!sheet) {
    sheet = ss.insertSheet(SHEET_NAME);
    sheet.appendRow(HEADERS);
    sheet.getRange(1, 1, 1, HEADERS.length).setFontWeight("bold");
    sheet.setFrozenRows(1);
  } else {
    ensureHeaders(sheet);
  }
  return sheet;
}

function getAppConfigSheet() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = ss.getSheetByName(CONFIG_SHEET_NAME);
  if (!sheet) {
    sheet = ss.insertSheet(CONFIG_SHEET_NAME);
    sheet.appendRow(APP_CONFIG_HEADERS);
    sheet.getRange(1, 1, 1, APP_CONFIG_HEADERS.length).setFontWeight("bold");
    sheet.setFrozenRows(1);
    sheet.appendRow([
      DEFAULT_APP_CONFIG.portal_url,
      DEFAULT_APP_CONFIG.minimum_version_code,
      DEFAULT_APP_CONFIG.latest_version_name,
      DEFAULT_APP_CONFIG.apk_download_url,
      DEFAULT_APP_CONFIG.update_message,
      DEFAULT_APP_CONFIG.force_update ? "true" : "false",
      nowStr()
    ]);
  } else if (sheet.getLastColumn() === 0) {
    sheet.appendRow(APP_CONFIG_HEADERS);
    sheet.getRange(1, 1, 1, APP_CONFIG_HEADERS.length).setFontWeight("bold");
    sheet.setFrozenRows(1);
  }
  return sheet;
}

function readAppConfig() {
  const sheet = getAppConfigSheet();
  const cfg = {
    portal_url: DEFAULT_APP_CONFIG.portal_url,
    minimum_version_code: DEFAULT_APP_CONFIG.minimum_version_code,
    latest_version_name: DEFAULT_APP_CONFIG.latest_version_name,
    apk_download_url: DEFAULT_APP_CONFIG.apk_download_url,
    update_message: DEFAULT_APP_CONFIG.update_message,
    force_update: DEFAULT_APP_CONFIG.force_update
  };
  const lastRow = sheet.getLastRow();
  const lastCol = sheet.getLastColumn();
  if (lastRow < 2 || lastCol < 1) {
    return cfg;
  }

  const headerRow = sheet.getRange(1, 1, 1, lastCol).getValues()[0];
  const normHeaders = headerRow.map(normalizeHeaderKey);
  const firstHeader = normHeaders[0] || "";

  // Support optional key-value vertical format as well as flat header row format
  if ((firstHeader === "key" || firstHeader === "configkey" || firstHeader === "setting") && lastCol >= 2) {
    const kvRows = sheet.getRange(2, 1, lastRow - 1, 2).getValues();
    for (let i = 0; i < kvRows.length; i++) {
      const k = normalizeHeaderKey(kvRows[i][0]);
      const v = kvRows[i][1];
      applyConfigEntry(cfg, k, v);
    }
    return cfg;
  }

  const row = sheet.getRange(2, 1, 1, lastCol).getValues()[0];
  for (let c = 0; c < normHeaders.length; c++) {
    applyConfigEntry(cfg, normHeaders[c], row[c]);
  }
  return cfg;
}

function applyConfigEntry(cfg, normalizedKey, rawVal) {
  const strVal = rawVal === undefined || rawVal === null ? "" : String(rawVal).trim();
  switch (normalizedKey) {
    case "portalurl":
      if (strVal && /^https?:\/\//i.test(strVal)) {
        cfg.portal_url = strVal;
      }
      break;
    case "minimumversioncode":
    case "minversioncode":
      if (strVal) {
        cfg.minimum_version_code = Math.max(1, toInt(strVal, 1));
      }
      break;
    case "latestversionname":
    case "versionname":
      if (strVal) {
        cfg.latest_version_name = strVal;
      }
      break;
    case "apkdownloadurl":
    case "downloadurl":
      cfg.apk_download_url = strVal;
      break;
    case "updatemessage":
      cfg.update_message = strVal;
      break;
    case "forceupdate":
      cfg.force_update = toBool(rawVal);
      break;
  }
}

function writeAppConfig(patch) {
  const current = readAppConfig();
  const next = {
    portal_url: (patch.portal_url !== undefined ? String(patch.portal_url).trim() : current.portal_url) || DEFAULT_APP_CONFIG.portal_url,
    minimum_version_code: patch.minimum_version_code !== undefined ? Math.max(1, toInt(patch.minimum_version_code, current.minimum_version_code)) : current.minimum_version_code,
    latest_version_name: (patch.latest_version_name !== undefined ? String(patch.latest_version_name).trim() : current.latest_version_name) || DEFAULT_APP_CONFIG.latest_version_name,
    apk_download_url: patch.apk_download_url !== undefined ? String(patch.apk_download_url).trim() : current.apk_download_url,
    update_message: patch.update_message !== undefined ? String(patch.update_message).trim() : current.update_message,
    force_update: patch.force_update !== undefined ? toBool(patch.force_update) : current.force_update
  };
  if (!/^https?:\/\//i.test(next.portal_url)) {
    next.portal_url = DEFAULT_APP_CONFIG.portal_url;
  }

  const sheet = getAppConfigSheet();
  const lastCol = Math.max(sheet.getLastColumn(), APP_CONFIG_HEADERS.length);
  const headerRow = sheet.getRange(1, 1, 1, lastCol).getValues()[0];
  const normHeaders = headerRow.map(normalizeHeaderKey);
  if ((normHeaders[0] === "key" || normHeaders[0] === "configkey" || normHeaders[0] === "setting") && lastCol >= 2) {
    const keysOrder = ["portal_url", "minimum_version_code", "latest_version_name", "apk_download_url", "update_message", "force_update"];
    const ts = nowStr();
    sheet.getRange(1, 1, 1, 3).setValues([["key", "value", "updated_at"]]).setFontWeight("bold");
    const rows = keysOrder.map(k => [k, k === "force_update" ? (next[k] ? "true" : "false") : next[k], ts]);
    sheet.getRange(2, 1, rows.length, 3).setValues(rows);
    return next;
  }

  sheet.getRange(1, 1, 1, APP_CONFIG_HEADERS.length).setValues([APP_CONFIG_HEADERS]).setFontWeight("bold");
  sheet.setFrozenRows(1);
  sheet.getRange(2, 1, 1, APP_CONFIG_HEADERS.length).setValues([[
    next.portal_url,
    next.minimum_version_code,
    next.latest_version_name,
    next.apk_download_url,
    next.update_message,
    next.force_update ? "true" : "false",
    nowStr()
  ]]);
  return next;
}

function getToolPermissionsSheet() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = ss.getSheetByName(TOOL_PERMISSIONS_SHEET_NAME);
  if (!sheet) {
    sheet = ss.insertSheet(TOOL_PERMISSIONS_SHEET_NAME);
    sheet.appendRow(TOOL_PERMISSION_HEADERS);
    sheet.getRange(1, 1, 1, TOOL_PERMISSION_HEADERS.length).setFontWeight("bold");
    sheet.setFrozenRows(1);
  } else if (sheet.getLastColumn() === 0) {
    sheet.appendRow(TOOL_PERMISSION_HEADERS);
    sheet.getRange(1, 1, 1, TOOL_PERMISSION_HEADERS.length).setFontWeight("bold");
    sheet.setFrozenRows(1);
  }
  return sheet;
}

function normalizeToolKey(raw) {
  const norm = normalizeHeaderKey(raw);
  switch (norm) {
    case "formative":
    case "formativefiller":
      return "formative";
    case "talk":
    case "talktofiller":
      return "talk";
    case "lpcd":
    case "lpcdfiller":
      return "lpcd";
    case "bco":
    case "bcofiller":
      return "bco";
    case "excel":
    case "excelfiller":
      return "excel";
    case "marksheet":
    case "bulkmarksheet":
    case "bulkmarksheetdownloader":
      return "marksheet";
    case "resultready":
    case "result":
    case "resultreadytool":
      return "result_ready";
    case "hprcscan":
    case "hprc":
    case "hprcscantool":
      return "hprc_scan";
    default:
      return "";
  }
}

function defaultToolPermissions() {
  return {
    formative: true,
    talk: true,
    lpcd: true,
    bco: true,
    excel: true,
    marksheet: true,
    result_ready: true,
    hprc_scan: true
  };
}

function parsePermissionCell(val) {
  if (val === undefined || val === null) return true;
  if (val === false) return false;
  if (val === true) return true;
  const s = String(val).trim().toLowerCase();
  if (!s) return true;
  if (s === "false" || s === "0" || s === "no" || s === "off" || s === "disabled" || s === "blocked" || s === "deny") {
    return false;
  }
  return true;
}

function readAllToolPermissionsMap() {
  const sheet = getToolPermissionsSheet();
  const lastRow = sheet.getLastRow();
  const lastCol = Math.max(sheet.getLastColumn(), TOOL_PERMISSION_HEADERS.length);
  const headerRow = sheet.getRange(1, 1, 1, lastCol).getValues()[0];
  const colByTool = {};
  let userIdCol = 0;
  for (let c = 0; c < headerRow.length; c++) {
    const hNorm = normalizeHeaderKey(headerRow[c]);
    if (hNorm === "userid" || hNorm === "id") {
      userIdCol = c;
    } else {
      const tk = normalizeToolKey(hNorm);
      if (tk) colByTool[tk] = c;
    }
  }

  const mapByUserId = {};
  if (lastRow > 1) {
    const rows = sheet.getRange(2, 1, lastRow - 1, lastCol).getValues();
    for (let r = 0; r < rows.length; r++) {
      const uid = String(rows[r][userIdCol] || "").trim().toUpperCase();
      if (!uid) continue;
      const perms = defaultToolPermissions();
      for (let t = 0; t < TOOL_KEYS.length; t++) {
        const key = TOOL_KEYS[t];
        const colIdx = colByTool[key];
        if (colIdx !== undefined && colIdx >= 0) {
          perms[key] = parsePermissionCell(rows[r][colIdx]);
        }
      }
      mapByUserId[uid] = perms;
    }
  }
  return mapByUserId;
}

function getUserToolPermissions(userId, cachedMap) {
  const uid = String(userId || "").trim().toUpperCase();
  if (!uid) return defaultToolPermissions();
  const map = cachedMap || readAllToolPermissionsMap();
  return map[uid] || defaultToolPermissions();
}

function writeUserToolPermissions(userId, incomingPerms, adminActor) {
  const uid = String(userId || "").trim().toUpperCase();
  if (!uid) return defaultToolPermissions();
  const sheet = getToolPermissionsSheet();
  const lastCol = Math.max(sheet.getLastColumn(), TOOL_PERMISSION_HEADERS.length);
  const headerRow = sheet.getRange(1, 1, 1, lastCol).getValues()[0];
  const headerMap = {};
  for (let c = 0; c < headerRow.length; c++) {
    headerMap[normalizeHeaderKey(headerRow[c])] = c;
  }

  // Ensure canonical columns exist if sheet was manually created cleanly
  if (headerMap["userid"] === undefined) {
    sheet.getRange(1, 1, 1, TOOL_PERMISSION_HEADERS.length).setValues([TOOL_PERMISSION_HEADERS]).setFontWeight("bold");
    sheet.setFrozenRows(1);
    for (let c = 0; c < TOOL_PERMISSION_HEADERS.length; c++) {
      headerMap[normalizeHeaderKey(TOOL_PERMISSION_HEADERS[c])] = c;
    }
  }

  const userIdCol = headerMap["userid"] !== undefined ? headerMap["userid"] : 0;
  const lastRow = sheet.getLastRow();
  let targetRowIdx = -1;
  let existingRow = null;
  if (lastRow > 1) {
    const rows = sheet.getRange(2, 1, lastRow - 1, Math.max(sheet.getLastColumn(), TOOL_PERMISSION_HEADERS.length)).getValues();
    for (let i = 0; i < rows.length; i++) {
      if (String(rows[i][userIdCol] || "").trim().toUpperCase() === uid) {
        targetRowIdx = i + 2;
        existingRow = rows[i];
        break;
      }
    }
  }

  const resolved = defaultToolPermissions();
  for (let t = 0; t < TOOL_KEYS.length; t++) {
    const key = TOOL_KEYS[t];
    if (existingRow) {
      const col = headerMap[normalizeHeaderKey(key)];
      if (col !== undefined && col >= 0) {
        resolved[key] = parsePermissionCell(existingRow[col]);
      }
    }
    if (incomingPerms && incomingPerms[key] !== undefined) {
      resolved[key] = parsePermissionCell(incomingPerms[key]);
    }
  }

  const rowValues = [
    uid,
    resolved.formative ? "true" : "false",
    resolved.talk ? "true" : "false",
    resolved.lpcd ? "true" : "false",
    resolved.bco ? "true" : "false",
    resolved.excel ? "true" : "false",
    resolved.marksheet ? "true" : "false",
    resolved.result_ready ? "true" : "false",
    resolved.hprc_scan ? "true" : "false",
    nowStr(),
    String(adminActor || ADMIN_ID)
  ];

  if (targetRowIdx > 0) {
    sheet.getRange(targetRowIdx, 1, 1, rowValues.length).setValues([rowValues]);
  } else {
    sheet.appendRow(rowValues);
  }
  return resolved;
}

function normalizePhone(raw) {
  const digits = String(raw || "").replace(/\D+/g, "");
  if (digits.length > 10) {
    if (digits.startsWith("91") && digits.length >= 12) {
      return digits.slice(digits.length - 10);
    }
    if (digits.startsWith("0") && digits.length === 11) {
      return digits.slice(1);
    }
    return digits.slice(digits.length - 10);
  }
  return digits;
}

function sha256Hex(input) {
  const bytes = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, String(input), Utilities.Charset.UTF_8);
  return bytes.map(b => ((b < 0 ? b + 256 : b).toString(16)).padStart(2, "0")).join("");
}

function hashPassword(plain) {
  const salt = Utilities.getUuid().replace(/-/g, "").slice(0, 16);
  return "sha256s:" + salt + ":" + sha256Hex(salt + ":" + String(plain));
}

function hashLegacySha256(plain) {
  return "sha256:" + sha256Hex(String(plain));
}

function verifyPassword(stored, inputPlain) {
  const s = String(stored || "");
  const p = String(inputPlain || "");
  const sTrimmed = s.trim();
  const pTrimmed = p.trim();
  if (!sTrimmed) return false;

  if (sTrimmed.startsWith("sha256s:")) {
    const parts = sTrimmed.split(":");
    if (parts.length !== 3) return false;
    const salt = parts[1];
    const expectedHash = parts[2];
    return sha256Hex(salt + ":" + p) === expectedHash || sha256Hex(salt + ":" + pTrimmed) === expectedHash;
  }
  if (sTrimmed.startsWith("sha256:")) {
    return sTrimmed === hashLegacySha256(p) || sTrimmed === hashLegacySha256(pTrimmed);
  }
  return s === p || sTrimmed === pTrimmed;
}

function toBool(val) {
  if (val === true) return true;
  const s = String(val || "").trim().toLowerCase();
  return s === "true" || s === "1" || s === "yes" || s === "active";
}

function toInt(val, fallback) {
  const n = parseInt(String(val === undefined || val === null ? "" : val).trim(), 10);
  return isNaN(n) ? (fallback || 0) : n;
}

function todayStr() {
  return Utilities.formatDate(new Date(), TIMEZONE, "yyyy-MM-dd");
}

function nowStr() {
  return Utilities.formatDate(new Date(), TIMEZONE, "yyyy-MM-dd HH:mm:ss");
}

function addDaysStr(days) {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return Utilities.formatDate(d, TIMEZONE, "yyyy-MM-dd");
}

function formatDateValue(val) {
  if (!val) return "";
  if (Object.prototype.toString.call(val) === "[object Date]" && !isNaN(val.getTime())) {
    return Utilities.formatDate(val, TIMEZONE, "yyyy-MM-dd");
  }
  const s = String(val).trim();
  if (!s) return "";
  const token = s.split("T")[0].split(" ")[0].replace(/\//g, "-");
  if (/^\d{4}-\d{2}-\d{2}$/.test(token)) {
    return token;
  }
  const dmy = /^(\d{2})-(\d{2})-(\d{4})$/.exec(token);
  if (dmy) {
    return dmy[3] + "-" + dmy[2] + "-" + dmy[1];
  }
  if (/^\d{4}-\d{2}-\d{2}/.test(s)) {
    return s.slice(0, 10);
  }
  return s;
}

function formatTimestampValue(val) {
  if (!val) return "";
  if (Object.prototype.toString.call(val) === "[object Date]" && !isNaN(val.getTime())) {
    return Utilities.formatDate(val, TIMEZONE, "yyyy-MM-dd HH:mm:ss");
  }
  return String(val).trim();
}

function readOptional(row, colIndex) {
  return colIndex >= 0 ? row[colIndex] : "";
}

function rowToUser(row, schema) {
  const userId = String(row[schema.userId] || "").trim().toUpperCase();
  const phone = normalizePhone(row[schema.phone]) || String(row[schema.phone] || "").trim();
  const status = String(row[schema.status] || "inactive").trim().toLowerCase() || "inactive";
  const expiryDate = formatDateValue(row[schema.expiryDate]);
  const activeDeviceId = String(row[schema.activeDeviceId] || "").trim();
  const lastLogin = formatTimestampValue(row[schema.lastLogin]);

  const fullName = String(readOptional(row, schema.fullName) || "").trim();
  const username = String(readOptional(row, schema.username) || "").trim();
  const role = String(readOptional(row, schema.role) || "general").trim().toLowerCase() || "general";
  const hasAppLoggedInCol = schema.appLoggedIn >= 0;
  const appLoggedIn = hasAppLoggedInCol
    ? toBool(row[schema.appLoggedIn])
    : (activeDeviceId !== "" && status === "active" && !!expiryDate && todayStr() <= expiryDate);

  const out = {
    userId: userId,
    user_id: userId,
    phone: phone,
    phone_number: phone,
    status: status,
    expiryDate: expiryDate,
    expiry_date: expiryDate,
    activeDeviceId: activeDeviceId,
    active_device_id: activeDeviceId,
    lastLogin: lastLogin,
    last_login: lastLogin,
    appLoggedIn: appLoggedIn
  };

  if (fullName) out.fullName = fullName;
  if (username) out.username = username;
  if (schema.role >= 0) out.role = role;
  if (schema.lastActivity >= 0) out.lastActivity = String(row[schema.lastActivity] || "").trim();
  if (schema.createdAt >= 0) out.createdAt = formatTimestampValue(row[schema.createdAt]);
  if (schema.portalSessionActive >= 0) out.portalSessionActive = toBool(row[schema.portalSessionActive]);
  if (schema.portalResetVersion >= 0) out.portalResetVersion = toInt(row[schema.portalResetVersion], 0);
  if (schema.portalLastUpdated >= 0) out.portalLastUpdated = formatTimestampValue(row[schema.portalLastUpdated]);

  return out;
}

function handleRegister(body) {
  const userId = String(body.userId || body.user_id || body.id || "").trim().toUpperCase();
  const phone = normalizePhone(body.phone || body.phone_number || body.mobile || "");
  const password = String(body.password || body.pass || "").trim();
  const deviceId = String(body.deviceId || body.device_id || "").trim();
  const fullName = String(body.fullName || body.full_name || body.name || "").trim();
  const rawUsername = String(body.username || body.user_name || "").trim();

  if (!userId || userId.length < 4) {
    return { ok: false, status: "error", code: "INVALID_INPUT", message: "Valid User ID is required." };
  }
  if (!phone || phone.length < 10) {
    return { ok: false, status: "error", code: "INVALID_PHONE", message: "Please enter a valid 10-digit phone number." };
  }
  if (!password || password.length < 4) {
    return { ok: false, status: "error", code: "WEAK_PASSWORD", message: "Password must be at least 4 characters." };
  }

  const sheet = getUsersSheet();
  const schema = getSheetSchema(sheet);
  const lastRow = sheet.getLastRow();
  if (lastRow > 1) {
    const values = sheet.getRange(2, 1, lastRow - 1, schema.colCount).getValues();
    for (let i = 0; i < values.length; i++) {
      const existingId = String(values[i][schema.userId] || "").trim().toUpperCase();
      const existingPhone = normalizePhone(values[i][schema.phone]);
      if (existingId === userId) {
        return { ok: false, status: "error", code: "USER_ID_EXISTS", message: "User ID already exists. Generate a new ID and try again." };
      }
      if (existingPhone === phone) {
        return { ok: false, status: "error", code: "PHONE_EXISTS", message: "This phone number is already registered. Please use General Login." };
      }
      if (schema.username >= 0 && rawUsername) {
        const existingUsername = String(values[i][schema.username] || "").trim().toLowerCase();
        if (existingUsername && existingUsername === rawUsername.toLowerCase()) {
          return { ok: false, status: "error", code: "USERNAME_EXISTS", message: "This username is already taken. Choose another username." };
        }
      }
    }
  }

  const createdAt = nowStr();
  const newRow = new Array(schema.colCount).fill("");
  newRow[schema.userId] = userId;
  newRow[schema.phone] = phone;
  newRow[schema.password] = hashPassword(password);
  newRow[schema.status] = "inactive";
  newRow[schema.expiryDate] = "";
  newRow[schema.activeDeviceId] = deviceId;
  newRow[schema.lastLogin] = "";

  if (schema.lastActivity >= 0) newRow[schema.lastActivity] = "Registered (awaiting admin activation)";
  if (schema.createdAt >= 0) newRow[schema.createdAt] = createdAt;
  if (schema.fullName >= 0 && fullName) newRow[schema.fullName] = fullName;
  if (schema.username >= 0 && rawUsername) newRow[schema.username] = rawUsername;
  if (schema.role >= 0) newRow[schema.role] = "general";
  if (schema.appLoggedIn >= 0) newRow[schema.appLoggedIn] = "false";
  if (schema.sessionToken >= 0) newRow[schema.sessionToken] = "";
  if (schema.portalSessionActive >= 0) newRow[schema.portalSessionActive] = "false";
  if (schema.portalResetVersion >= 0) newRow[schema.portalResetVersion] = 0;
  if (schema.portalLastUpdated >= 0) newRow[schema.portalLastUpdated] = "";

  sheet.appendRow(newRow);

  const newRowIdx = sheet.getLastRow();
  sheet.getRange(newRowIdx, schema.phone + 1).setNumberFormat("@").setValue(phone);
  if (schema.username >= 0 && rawUsername) {
    sheet.getRange(newRowIdx, schema.username + 1).setNumberFormat("@").setValue(rawUsername);
  }

  return {
    ok: true,
    status: "success",
    code: "REGISTERED",
    message: "Registration successful. Your account is inactive until activated by Admin.",
    user: rowToUser(sheet.getRange(newRowIdx, 1, 1, schema.colCount).getValues()[0], schema)
  };
}

function findUserRow(sheet, schema, identifier) {
  const lastRow = sheet.getLastRow();
  if (lastRow <= 1) return null;

  const rawTrimmed = String(identifier || "").trim();
  const idUpper = rawTrimmed.toUpperCase();
  const idLower = rawTrimmed.toLowerCase();
  const phoneNorm = normalizePhone(rawTrimmed);
  const values = sheet.getRange(2, 1, lastRow - 1, schema.colCount).getValues();

  for (let i = 0; i < values.length; i++) {
    const rowUserId = String(values[i][schema.userId] || "").trim().toUpperCase();
    const rowPhoneRaw = String(values[i][schema.phone] || "").trim();
    const rowPhone = normalizePhone(rowPhoneRaw);
    const rowUsername = schema.username >= 0
      ? String(values[i][schema.username] || "").trim().toLowerCase()
      : "";
    if (
      (idUpper && rowUserId === idUpper) ||
      (idLower && rowUsername && rowUsername === idLower) ||
      (phoneNorm && phoneNorm.length >= 10 && rowPhone === phoneNorm) ||
      (rawTrimmed && rowPhoneRaw === rawTrimmed)
    ) {
      return { rowIndex: i + 2, row: values[i] };
    }
  }
  return null;
}

function handleLogin(body) {
  const identifier = String(
    body.identifier ||
    body.userId ||
    body.user_id ||
    body.phone ||
    body.phone_number ||
    body.mobile ||
    body.username ||
    ""
  ).trim();
  const password = String(body.password || body.pass || "").trim();
  const deviceId = String(body.deviceId || body.device_id || "").trim();

  if (!identifier || !password) {
    return { ok: false, status: "error", code: "INVALID_INPUT", message: "Enter User ID or Phone Number and Password." };
  }
  if (!deviceId) {
    return { ok: false, status: "error", code: "DEVICE_REQUIRED", message: "Device ID unavailable." };
  }

  const sheet = getUsersSheet();
  const schema = getSheetSchema(sheet);
  const found = findUserRow(sheet, schema, identifier);
  if (!found) {
    return { ok: false, status: "error", code: "NOT_FOUND", message: "Account not found. Please register first." };
  }

  const { rowIndex, row } = found;
  const storedPwd = String(row[schema.password] || "");
  if (!verifyPassword(storedPwd, password)) {
    return { ok: false, status: "error", code: "BAD_PASSWORD", message: "Incorrect password." };
  }

  if (!storedPwd.trim().startsWith("sha256s:")) {
    sheet.getRange(rowIndex, schema.password + 1).setValue(hashPassword(password));
  }

  const user = rowToUser(row, schema);
  const displayId = user.userId || user.phone || identifier;
  if (user.status !== "active") {
    return {
      ok: false,
      status: "error",
      code: "INACTIVE",
      message: "Your account (" + displayId + ") is inactive. Contact Admin for activation.",
      user: user
    };
  }

  let expiryDate = user.expiryDate;
  if (!expiryDate) {
    expiryDate = addDaysStr(30);
    sheet.getRange(rowIndex, schema.expiryDate + 1).setNumberFormat("@").setValue(expiryDate);
    user.expiryDate = expiryDate;
    user.expiry_date = expiryDate;
  }

  if (todayStr() > expiryDate) {
    return {
      ok: false,
      status: "error",
      code: "EXPIRED",
      message: "Your 1-month (30-day) subscription expired on " + expiryDate + ". Contact Admin to renew.",
      user: user
    };
  }

  if (user.activeDeviceId && user.activeDeviceId !== deviceId) {
    return {
      ok: false,
      status: "error",
      code: "DEVICE_LOCKED",
      message: "Account locked to another device (" + user.activeDeviceId.slice(0, 8) + "...). Ask Admin to reset device lock.",
      user: user
    };
  }

  const loginTs = nowStr();
  const sessionToken = Utilities.getUuid().replace(/-/g, "");
  if (!user.activeDeviceId) {
    sheet.getRange(rowIndex, schema.activeDeviceId + 1).setValue(deviceId);
    user.activeDeviceId = deviceId;
    user.active_device_id = deviceId;
  }
  sheet.getRange(rowIndex, schema.lastLogin + 1).setValue(loginTs);
  setCellIfPresent(sheet, rowIndex, schema.lastActivity, "Logged in (" + loginTs + ")");
  setCellIfPresent(sheet, rowIndex, schema.appLoggedIn, "true");
  setCellIfPresent(sheet, rowIndex, schema.sessionToken, sessionToken);

  user.lastLogin = loginTs;
  user.last_login = loginTs;
  user.appLoggedIn = true;
  const toolPermissions = getUserToolPermissions(user.userId);
  user.toolPermissions = toolPermissions;
  const appConfig = readAppConfig();

  return {
    ok: true,
    status: "success",
    code: "OK",
    message: "Login successful.",
    sessionToken: sessionToken,
    user: user,
    toolPermissions: toolPermissions,
    appConfig: appConfig
  };
}

function handleLogout(body) {
  const userId = String(body.userId || body.user_id || body.identifier || "").trim();
  const deviceId = String(body.deviceId || body.device_id || "").trim();
  if (!userId) {
    return { ok: true, status: "success", code: "OK", message: "Signed out." };
  }
  const sheet = getUsersSheet();
  const schema = getSheetSchema(sheet);
  const found = findUserRow(sheet, schema, userId);
  if (!found) {
    return { ok: true, status: "success", code: "OK", message: "Signed out." };
  }
  const { rowIndex, row } = found;
  const user = rowToUser(row, schema);
  if (user.activeDeviceId && deviceId && user.activeDeviceId !== deviceId) {
    return { ok: true, status: "success", code: "OK", message: "Signed out locally." };
  }
  setCellIfPresent(sheet, rowIndex, schema.appLoggedIn, "false");
  setCellIfPresent(sheet, rowIndex, schema.sessionToken, "");
  setCellIfPresent(sheet, rowIndex, schema.lastActivity, "Signed out (" + nowStr() + ")");
  return { ok: true, status: "success", code: "OK", message: "Signed out." };
}

function handleCheckSession(body) {
  const userId = String(body.userId || body.user_id || body.identifier || "").trim();
  const deviceId = String(body.deviceId || body.device_id || "").trim();
  const clientToken = String(body.sessionToken || body.session_token || "").trim();
  if (!userId || !deviceId) {
    return { ok: false, status: "error", code: "INVALID_SESSION", message: "Session invalid." };
  }

  const sheet = getUsersSheet();
  const schema = getSheetSchema(sheet);
  const found = findUserRow(sheet, schema, userId);
  if (!found) {
    return { ok: false, status: "error", code: "NOT_FOUND", message: "Account no longer exists." };
  }

  const { rowIndex, row } = found;
  const user = rowToUser(row, schema);

  if (user.status !== "active") {
    return {
      ok: false,
      status: "error",
      code: "INACTIVE",
      message: "Your account has been deactivated by Admin. Access is blocked until reactivated.",
      user: user
    };
  }

  if (!user.expiryDate || todayStr() > user.expiryDate) {
    return {
      ok: false,
      status: "error",
      code: "EXPIRED",
      message: "Your 1-month (30-day) subscription expired on " + (user.expiryDate || "N/A") + ". Contact Admin to renew.",
      user: user
    };
  }

  if (!user.activeDeviceId || user.activeDeviceId !== deviceId) {
    return {
      ok: false,
      status: "error",
      code: "DEVICE_LOCKED",
      message: "Device lock mismatch or reset by Admin. Please sign in again.",
      user: user
    };
  }

  if (schema.sessionToken >= 0) {
    const storedToken = String(row[schema.sessionToken] || "").trim();
    if (storedToken && clientToken && storedToken !== clientToken) {
      return {
        ok: false,
        status: "error",
        code: "SESSION_REVOKED",
        message: "Your session was ended or password was reset by Admin. Please sign in again.",
        user: user
      };
    }
  }

  const toolPermissions = getUserToolPermissions(user.userId);
  user.toolPermissions = toolPermissions;
  const appConfig = readAppConfig();

  const requestedToolRaw = body.tool || body.toolKey || body.tool_key || "";
  const requestedTool = normalizeToolKey(requestedToolRaw);
  if (requestedTool && toolPermissions[requestedTool] === false) {
    return {
      ok: false,
      status: "error",
      code: "TOOL_DENIED",
      message: "Access to tool '" + requestedTool + "' is disabled by Admin.",
      tool: requestedTool,
      user: user,
      toolPermissions: toolPermissions,
      appConfig: appConfig
    };
  }

  setCellIfPresent(sheet, rowIndex, schema.appLoggedIn, "true");
  setCellIfPresent(sheet, rowIndex, schema.lastActivity, "Session verified (" + nowStr() + ")");
  user.appLoggedIn = true;
  return {
    ok: true,
    status: "success",
    code: "OK",
    message: "Session valid.",
    user: user,
    toolPermissions: toolPermissions,
    appConfig: appConfig
  };
}

function handleUpdateActivity(body) {
  const userId = String(body.userId || body.user_id || body.identifier || "").trim();
  const deviceId = String(body.deviceId || body.device_id || "").trim();
  const activity = String(body.activity || "Active").trim();

  const sheet = getUsersSheet();
  const schema = getSheetSchema(sheet);
  const found = findUserRow(sheet, schema, userId);
  if (!found) {
    return { ok: false, status: "error", code: "NOT_FOUND", message: "Account not found." };
  }
  const { rowIndex, row } = found;
  const user = rowToUser(row, schema);
  if (user.status !== "active") {
    return { ok: false, status: "error", code: "INACTIVE", message: "Account is inactive." };
  }
  if (!user.expiryDate || todayStr() > user.expiryDate) {
    return { ok: false, status: "error", code: "EXPIRED", message: "Subscription expired." };
  }
  if (user.activeDeviceId && deviceId && user.activeDeviceId !== deviceId) {
    return { ok: false, status: "error", code: "DEVICE_LOCKED", message: "Device lock mismatch." };
  }
  const toolPermissions = getUserToolPermissions(user.userId);
  const requestedTool = normalizeToolKey(body.tool || body.toolKey || body.tool_key || "");
  if (requestedTool && toolPermissions[requestedTool] === false) {
    return {
      ok: false,
      status: "error",
      code: "TOOL_DENIED",
      message: "Access to tool '" + requestedTool + "' is disabled by Admin.",
      tool: requestedTool,
      toolPermissions: toolPermissions
    };
  }
  setCellIfPresent(sheet, rowIndex, schema.appLoggedIn, "true");
  setCellIfPresent(sheet, rowIndex, schema.lastActivity, activity + " (" + nowStr() + ")");
  return { ok: true, status: "success", code: "OK", message: "Activity updated.", toolPermissions: toolPermissions };
}

function handleVerifyToolAccess(body) {
  return handleCheckSession(body);
}

function handleGetAppConfig() {
  return {
    ok: true,
    status: "success",
    code: "OK",
    message: "App config loaded.",
    appConfig: readAppConfig()
  };
}

function handleReportPortalState(body) {
  const userId = String(body.userId || body.user_id || body.identifier || "").trim();
  const deviceId = String(body.deviceId || body.device_id || "").trim();
  const portalActive = toBool(body.portalSessionActive !== undefined ? body.portalSessionActive : body.portal_session_active);
  const activity = String(body.activity || "").trim();

  const sheet = getUsersSheet();
  const schema = getSheetSchema(sheet);
  const found = findUserRow(sheet, schema, userId);
  if (!found) {
    return { ok: false, status: "error", code: "NOT_FOUND", message: "Account not found." };
  }
  const { rowIndex, row } = found;
  const user = rowToUser(row, schema);
  if (user.activeDeviceId && deviceId && user.activeDeviceId !== deviceId) {
    return { ok: false, status: "error", code: "DEVICE_LOCKED", message: "Device lock mismatch." };
  }
  const ts = nowStr();
  setCellIfPresent(sheet, rowIndex, schema.portalSessionActive, portalActive ? "true" : "false");
  setCellIfPresent(sheet, rowIndex, schema.portalLastUpdated, ts);
  if (activity) {
    setCellIfPresent(sheet, rowIndex, schema.lastActivity, activity + " (" + ts + ")");
  }
  return {
    ok: true,
    status: "success",
    code: "OK",
    message: "Portal status recorded.",
    user: rowToUser(sheet.getRange(rowIndex, 1, 1, schema.colCount).getValues()[0], schema)
  };
}

function verifyAdmin(body) {
  const adminId = String(body.adminId || body.admin_id || "").trim();
  const adminPass = String(body.adminPassword || body.admin_password || "").trim();
  return adminId === ADMIN_ID && adminPass === ADMIN_PASSWORD;
}

function handleAdminLogin(body) {
  if (!verifyAdmin(body)) {
    return { ok: false, status: "error", code: "UNAUTHORIZED", message: "Invalid Admin ID or Password." };
  }
  return { ok: true, status: "success", code: "ADMIN_OK", message: "Admin verified." };
}

function computeStats(users) {
  const today = todayStr();
  let activeUsers = 0;
  let inactiveUsers = 0;
  let expiredUsers = 0;
  let loggedInAppUsers = 0;
  let activePortalUsers = 0;

  for (let i = 0; i < users.length; i++) {
    const u = users[i];
    const isActive = u.status === "active";
    const isExpired = !u.expiryDate || today > u.expiryDate;
    if (!isActive) {
      inactiveUsers++;
    } else if (isExpired) {
      expiredUsers++;
    } else {
      activeUsers++;
    }
    if (u.appLoggedIn && isActive && !isExpired) {
      loggedInAppUsers++;
    }
    if (u.portalSessionActive && isActive && !isExpired) {
      activePortalUsers++;
    }
  }

  return {
    totalGeneralUsers: users.length,
    activeUsers: activeUsers,
    inactiveUsers: inactiveUsers,
    expiredUsers: expiredUsers,
    loggedInAppUsers: loggedInAppUsers,
    activePortalUsers: activePortalUsers
  };
}

function handleAdminListUsers(body) {
  if (!verifyAdmin(body)) {
    return { ok: false, status: "error", code: "UNAUTHORIZED", message: "Unauthorized admin credentials." };
  }
  const sheet = getUsersSheet();
  const schema = getSheetSchema(sheet);
  const permsMap = readAllToolPermissionsMap();
  const appConfig = readAppConfig();
  const lastRow = sheet.getLastRow();
  const users = [];
  if (lastRow > 1) {
    const values = sheet.getRange(2, 1, lastRow - 1, schema.colCount).getValues();
    for (let i = 0; i < values.length; i++) {
      if (!values[i][schema.userId] && !values[i][schema.phone]) continue;
      const u = rowToUser(values[i], schema);
      u.toolPermissions = getUserToolPermissions(u.userId, permsMap);
      users.push(u);
    }
  }
  return {
    ok: true,
    status: "success",
    code: "OK",
    message: "Loaded " + users.length + " user(s).",
    users: users,
    stats: computeStats(users),
    appConfig: appConfig
  };
}

function resolveTargetUserId(body) {
  return String(
    body.targetUserId ||
    body.target_user_id ||
    body.userId ||
    body.user_id ||
    body.id ||
    body.identifier ||
    body.phone_number ||
    body.phone ||
    body.mobile ||
    ""
  ).trim();
}

function findTargetUserRow(sheet, schema, body) {
  const primary = resolveTargetUserId(body);
  let found = primary ? findUserRow(sheet, schema, primary) : null;
  if (!found) {
    const phoneFallback = String(body.phone_number || body.phone || body.mobile || "").trim();
    if (phoneFallback && phoneFallback !== primary) {
      found = findUserRow(sheet, schema, phoneFallback);
    }
  }
  return { found: found, targetLabel: primary || String(body.phone_number || body.phone || "").trim() };
}

function handleAdminActivateUser(body) {
  if (!verifyAdmin(body)) {
    return { ok: false, status: "error", code: "UNAUTHORIZED", message: "Unauthorized admin credentials." };
  }
  const sheet = getUsersSheet();
  const schema = getSheetSchema(sheet);
  const lookup = findTargetUserRow(sheet, schema, body);
  if (!lookup.found) {
    return { ok: false, status: "error", code: "NOT_FOUND", message: "User not found: " + lookup.targetLabel };
  }
  const found = lookup.found;

  const providedExpiry = formatDateValue(body.expiryDate || body.expiry_date || "");
  const expiry = (/^\d{4}-\d{2}-\d{2}$/.test(providedExpiry) && providedExpiry >= todayStr())
    ? providedExpiry
    : addDaysStr(30);
  const ts = nowStr();
  sheet.getRange(found.rowIndex, schema.status + 1).setValue("active");
  sheet.getRange(found.rowIndex, schema.expiryDate + 1).setNumberFormat("@").setValue(expiry);
  sheet.getRange(found.rowIndex, schema.activeDeviceId + 1).setValue("");
  setCellIfPresent(sheet, found.rowIndex, schema.lastActivity, "Activated 30d & device unlocked by Admin (" + ts + ")");

  const updatedRow = sheet.getRange(found.rowIndex, 1, 1, schema.colCount).getValues()[0];
  const updatedUser = rowToUser(updatedRow, schema);
  updatedUser.toolPermissions = getUserToolPermissions(updatedUser.userId);
  return {
    ok: true,
    status: "success",
    code: "ACTIVATED",
    message: "Activated " + (updatedUser.userId || updatedUser.phone) + " until " + expiry + " (device lock reset)",
    user: updatedUser
  };
}

function handleAdminDeactivateUser(body) {
  if (!verifyAdmin(body)) {
    return { ok: false, status: "error", code: "UNAUTHORIZED", message: "Unauthorized admin credentials." };
  }
  const sheet = getUsersSheet();
  const schema = getSheetSchema(sheet);
  const lookup = findTargetUserRow(sheet, schema, body);
  if (!lookup.found) {
    return { ok: false, status: "error", code: "NOT_FOUND", message: "User not found: " + lookup.targetLabel };
  }
  const found = lookup.found;

  const ts = nowStr();
  sheet.getRange(found.rowIndex, schema.status + 1).setValue("inactive");
  setCellIfPresent(sheet, found.rowIndex, schema.appLoggedIn, "false");
  setCellIfPresent(sheet, found.rowIndex, schema.sessionToken, "");
  setCellIfPresent(sheet, found.rowIndex, schema.lastActivity, "Deactivated by Admin (" + ts + ")");

  const updatedRow = sheet.getRange(found.rowIndex, 1, 1, schema.colCount).getValues()[0];
  const updatedUser = rowToUser(updatedRow, schema);
  updatedUser.toolPermissions = getUserToolPermissions(updatedUser.userId);
  return {
    ok: true,
    status: "success",
    code: "DEACTIVATED",
    message: "Deactivated " + (updatedUser.userId || updatedUser.phone) + ".",
    user: updatedUser
  };
}

function handleAdminEditUser(body) {
  if (!verifyAdmin(body)) {
    return { ok: false, status: "error", code: "UNAUTHORIZED", message: "Unauthorized admin credentials." };
  }
  const sheet = getUsersSheet();
  const schema = getSheetSchema(sheet);
  const lookup = findTargetUserRow(sheet, schema, body);
  if (!lookup.found) {
    return { ok: false, status: "error", code: "NOT_FOUND", message: "User not found: " + lookup.targetLabel };
  }
  const found = lookup.found;

  const rawPhone = body.phone !== undefined ? body.phone : body.phone_number;
  const newPhone = rawPhone !== undefined ? normalizePhone(rawPhone) : "";
  if (rawPhone !== undefined && (!newPhone || newPhone.length < 10)) {
    return { ok: false, status: "error", code: "INVALID_PHONE", message: "Enter a valid 10-digit phone number." };
  }

  const lastRow = sheet.getLastRow();
  if (lastRow > 1 && newPhone) {
    const values = sheet.getRange(2, 1, lastRow - 1, schema.colCount).getValues();
    for (let i = 0; i < values.length; i++) {
      if (i + 2 === found.rowIndex) continue;
      if (normalizePhone(values[i][schema.phone]) === newPhone) {
        return { ok: false, status: "error", code: "PHONE_EXISTS", message: "Another user already uses phone " + newPhone };
      }
    }
  }

  const ts = nowStr();
  if (newPhone) {
    sheet.getRange(found.rowIndex, schema.phone + 1).setNumberFormat("@").setValue(newPhone);
  }
  if (schema.fullName >= 0 && (body.fullName !== undefined || body.full_name !== undefined)) {
    const newFullName = String(body.fullName !== undefined ? body.fullName : body.full_name).trim();
    sheet.getRange(found.rowIndex, schema.fullName + 1).setValue(newFullName);
  }
  if (schema.username >= 0 && (body.username !== undefined || body.user_name !== undefined)) {
    const newUsername = String(body.username !== undefined ? body.username : body.user_name).trim();
    sheet.getRange(found.rowIndex, schema.username + 1).setNumberFormat("@").setValue(newUsername);
  }
  setCellIfPresent(sheet, found.rowIndex, schema.lastActivity, "Profile updated by Admin (" + ts + ")");

  const updatedRow = sheet.getRange(found.rowIndex, 1, 1, schema.colCount).getValues()[0];
  const updatedUser = rowToUser(updatedRow, schema);
  updatedUser.toolPermissions = getUserToolPermissions(updatedUser.userId);
  return {
    ok: true,
    status: "success",
    code: "USER_UPDATED",
    message: "Updated details for " + (updatedUser.userId || updatedUser.phone),
    user: updatedUser
  };
}

function handleAdminResetPassword(body) {
  if (!verifyAdmin(body)) {
    return { ok: false, status: "error", code: "UNAUTHORIZED", message: "Unauthorized admin credentials." };
  }
  const newPassword = String(body.newPassword || body.new_password || body.password || "").trim();
  if (!newPassword || newPassword.length < 4) {
    return { ok: false, status: "error", code: "WEAK_PASSWORD", message: "New password must be at least 4 characters." };
  }

  const sheet = getUsersSheet();
  const schema = getSheetSchema(sheet);
  const lookup = findTargetUserRow(sheet, schema, body);
  if (!lookup.found) {
    return { ok: false, status: "error", code: "NOT_FOUND", message: "User not found: " + lookup.targetLabel };
  }
  const found = lookup.found;

  const ts = nowStr();
  sheet.getRange(found.rowIndex, schema.password + 1).setValue(hashPassword(newPassword));
  setCellIfPresent(sheet, found.rowIndex, schema.appLoggedIn, "false");
  setCellIfPresent(sheet, found.rowIndex, schema.sessionToken, "");
  setCellIfPresent(sheet, found.rowIndex, schema.lastActivity, "App password reset by Admin (" + ts + ")");

  const updatedRow = sheet.getRange(found.rowIndex, 1, 1, schema.colCount).getValues()[0];
  const updatedUser = rowToUser(updatedRow, schema);
  updatedUser.toolPermissions = getUserToolPermissions(updatedUser.userId);
  return {
    ok: true,
    status: "success",
    code: "PASSWORD_RESET",
    message: "App password securely reset for " + (updatedUser.userId || updatedUser.phone),
    user: updatedUser
  };
}

function handleAdminDeleteUser(body) {
  if (!verifyAdmin(body)) {
    return { ok: false, status: "error", code: "UNAUTHORIZED", message: "Unauthorized admin credentials." };
  }
  const sheet = getUsersSheet();
  const schema = getSheetSchema(sheet);
  const lookup = findTargetUserRow(sheet, schema, body);
  if (!lookup.found) {
    return { ok: false, status: "error", code: "NOT_FOUND", message: "User not found: " + lookup.targetLabel };
  }
  const found = lookup.found;
  const deletedUser = rowToUser(found.row, schema);
  sheet.deleteRow(found.rowIndex);
  return {
    ok: true,
    status: "success",
    code: "USER_DELETED",
    message: "Deleted account " + (deletedUser.userId || deletedUser.phone)
  };
}

function handleAdminResetDevice(body) {
  if (!verifyAdmin(body)) {
    return { ok: false, status: "error", code: "UNAUTHORIZED", message: "Unauthorized admin credentials." };
  }
  const sheet = getUsersSheet();
  const schema = getSheetSchema(sheet);
  const lookup = findTargetUserRow(sheet, schema, body);
  if (!lookup.found) {
    return { ok: false, status: "error", code: "NOT_FOUND", message: "User not found: " + lookup.targetLabel };
  }
  const found = lookup.found;

  const ts = nowStr();
  sheet.getRange(found.rowIndex, schema.activeDeviceId + 1).setValue("");
  setCellIfPresent(sheet, found.rowIndex, schema.lastActivity, "Device lock reset by Admin (" + ts + ")");

  const updatedRow = sheet.getRange(found.rowIndex, 1, 1, schema.colCount).getValues()[0];
  const updatedUser = rowToUser(updatedRow, schema);
  updatedUser.toolPermissions = getUserToolPermissions(updatedUser.userId);
  return {
    ok: true,
    status: "success",
    code: "DEVICE_RESET",
    message: "Device lock cleared for " + (updatedUser.userId || updatedUser.phone),
    user: updatedUser
  };
}

function handleAdminResetPortalSession(body, isForceLogout) {
  if (!verifyAdmin(body)) {
    return { ok: false, status: "error", code: "UNAUTHORIZED", message: "Unauthorized admin credentials." };
  }
  const sheet = getUsersSheet();
  const schema = getSheetSchema(sheet);
  const lookup = findTargetUserRow(sheet, schema, body);
  if (!lookup.found) {
    return { ok: false, status: "error", code: "NOT_FOUND", message: "User not found: " + lookup.targetLabel };
  }
  const found = lookup.found;

  const ts = nowStr();
  if (schema.portalResetVersion >= 0) {
    const currentVersion = toInt(found.row[schema.portalResetVersion], 0);
    setCellIfPresent(sheet, found.rowIndex, schema.portalResetVersion, currentVersion + 1);
  }
  setCellIfPresent(sheet, found.rowIndex, schema.portalSessionActive, "false");
  setCellIfPresent(sheet, found.rowIndex, schema.portalLastUpdated, ts);
  setCellIfPresent(
    sheet,
    found.rowIndex,
    schema.lastActivity,
    (isForceLogout ? "Portal forced logout by Admin (" : "Portal session reset by Admin (") + ts + ")"
  );

  const updatedRow = sheet.getRange(found.rowIndex, 1, 1, schema.colCount).getValues()[0];
  const updatedUser = rowToUser(updatedRow, schema);
  updatedUser.toolPermissions = getUserToolPermissions(updatedUser.userId);
  return {
    ok: true,
    status: "success",
    code: isForceLogout ? "PORTAL_LOGGED_OUT" : "PORTAL_SESSION_RESET",
    message: (isForceLogout ? "Forced portal logout" : "Portal session reset") +
      " for " + (updatedUser.userId || updatedUser.phone) + ".",
    user: updatedUser
  };
}

function handleAdminGetConfig(body) {
  if (!verifyAdmin(body)) {
    return { ok: false, status: "error", code: "UNAUTHORIZED", message: "Unauthorized admin credentials." };
  }
  return {
    ok: true,
    status: "success",
    code: "OK",
    message: "AppConfig loaded.",
    appConfig: readAppConfig()
  };
}

function handleAdminUpdateConfig(body) {
  if (!verifyAdmin(body)) {
    return { ok: false, status: "error", code: "UNAUTHORIZED", message: "Unauthorized admin credentials." };
  }
  const rawCfg = (body.appConfig && typeof body.appConfig === "object")
    ? body.appConfig
    : (body.config && typeof body.config === "object" ? body.config : body);
  const updated = writeAppConfig({
    portal_url: rawCfg.portal_url !== undefined ? rawCfg.portal_url : rawCfg.portalUrl,
    minimum_version_code: rawCfg.minimum_version_code !== undefined ? rawCfg.minimum_version_code : rawCfg.minimumVersionCode,
    latest_version_name: rawCfg.latest_version_name !== undefined ? rawCfg.latest_version_name : rawCfg.latestVersionName,
    apk_download_url: rawCfg.apk_download_url !== undefined ? rawCfg.apk_download_url : rawCfg.apkDownloadUrl,
    update_message: rawCfg.update_message !== undefined ? rawCfg.update_message : rawCfg.updateMessage,
    force_update: rawCfg.force_update !== undefined ? rawCfg.force_update : rawCfg.forceUpdate
  });
  return {
    ok: true,
    status: "success",
    code: "CONFIG_UPDATED",
    message: "AppConfig updated in Google Sheet.",
    appConfig: updated
  };
}

function handleAdminUpdateToolPermissions(body) {
  if (!verifyAdmin(body)) {
    return { ok: false, status: "error", code: "UNAUTHORIZED", message: "Unauthorized admin credentials." };
  }
  const sheet = getUsersSheet();
  const schema = getSheetSchema(sheet);
  const lookup = findTargetUserRow(sheet, schema, body);
  if (!lookup.found) {
    return { ok: false, status: "error", code: "NOT_FOUND", message: "User not found: " + lookup.targetLabel };
  }
  const found = lookup.found;
  const user = rowToUser(found.row, schema);

  let rawPerms = body.toolPermissions || body.tool_permissions || body.permissions || {};
  if (typeof rawPerms === "string") {
    try { rawPerms = JSON.parse(rawPerms); } catch (ignore) { rawPerms = {}; }
  }
  const normalizedIncoming = {};
  for (let i = 0; i < TOOL_KEYS.length; i++) {
    const tk = TOOL_KEYS[i];
    if (rawPerms && rawPerms[tk] !== undefined) {
      normalizedIncoming[tk] = rawPerms[tk];
    } else if (body[tk] !== undefined) {
      normalizedIncoming[tk] = body[tk];
    }
  }
  if (rawPerms && rawPerms.bulk_marksheet !== undefined && normalizedIncoming.marksheet === undefined) {
    normalizedIncoming.marksheet = rawPerms.bulk_marksheet;
  }

  const savedPerms = writeUserToolPermissions(user.userId, normalizedIncoming, body.adminId || body.admin_id || ADMIN_ID);
  const ts = nowStr();
  setCellIfPresent(sheet, found.rowIndex, schema.lastActivity, "Tool permissions updated by Admin (" + ts + ")");

  const updatedRow = sheet.getRange(found.rowIndex, 1, 1, schema.colCount).getValues()[0];
  const updatedUser = rowToUser(updatedRow, schema);
  updatedUser.toolPermissions = savedPerms;
  return {
    ok: true,
    status: "success",
    code: "PERMISSIONS_UPDATED",
    message: "Updated tool permissions for " + (updatedUser.userId || updatedUser.phone) + ".",
    user: updatedUser,
    toolPermissions: savedPerms
  };
}

function jsonOut(obj) {
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
