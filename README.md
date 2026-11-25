# BSP-SMS Android (Kotlin + Jetpack Compose)

Native Android wrapper for the **Banglarshiksha SMS Portal** (`https://www.school.banglarshiksha.gov.in/sms/`) and the **BSP-SMS** Chrome Extension tools.

## Included in this repository

- Jetpack Compose UI (`MainActivity.kt`) with a bilingual (**English / বাংলা**) `ModalNavigationDrawer` to switch between the Portal WebView, Tool Hub, and the five tools:
  1. Formative Filler (`formative/popup.html`)
  2. Talk to Filler (`talk/popup.html`)
  3. LPCD Filler (`lpcd/popup.html` + `lpcd/content.js`)
  4. BCO Filler (`bco/ab_form_filler_v2/popup.html` + `bco/ab_form_filler_v2/content.js`)
  5. Excel Filler (`excel/popup.html`)
- Chrome Extension compatibility bridge (`ChromeCompatBridge.kt` + `assets/extension/chrome-compat.js`):
  - `chrome.storage.local` (`get`, `set`, `remove`, `clear`) backed by Android `SharedPreferences`
  - `chrome.scripting.executeScript` executing JS files/functions against the active Portal WebView
  - `chrome.runtime.getURL` resolving local extension assets
  - `WebChromeClient.onShowFileChooser` wired to Android document picker for Excel (`.xlsx`, `.xls`) and CSV (`.csv`)
  - Runtime `RECORD_AUDIO` permission + `PermissionRequest.RESOURCE_AUDIO_CAPTURE` support for voice input
  - Persistent portal login cookies via Android `CookieManager` (no credentials are intercepted or transmitted to any third-party server)

## Adding your 5 tool subfolders

Place your existing Chrome Extension tool folders inside:

```
BSP-SMS-Android/app/src/main/assets/extension/
├── formative/
├── talk/
├── lpcd/
├── bco/ab_form_filler_v2/
└── excel/
```

The root extension files (`manifest.json`, `hub.html`, `hub.css`, `hub.js`, `background.js`, `launcher.js`) are already in `app/src/main/assets/extension/`.

## Authentication, 30-Day Activation & Admin Monitor (Serverless Google Sheets)

- **3-Tab Auth Screen (`AuthScreens.kt`)**:
  - **Register** (NO OTP): Auto-generates a `PBxxxxxx` User ID, accepts Phone Number + Password, and saves `inactive` accounts to Google Sheets.
  - **General Login**: Signs in with User ID or Phone Number + Password, checks `status == "active"`, enforces 30-day `expiry_date`, and enforces single-device lock (`active_device_id`).
  - **Admin Login**: Direct access with Admin credentials (`9679843774` / `9933115114`), no expiry limit, plus **Admin User Monitor** in the navigation drawer (`Activate 30d`, `Reset Device`, `Deactivate Account`).
- **Setup Guide**: See `GOOGLE_SHEETS_SETUP.md` and `google-apps-script/Code.gs`.
- **Custom Launcher Icon**: Saffron/gerua gradient (`#FF9933` → `#F57C00`) with bold centered white `PB SMS` lettering across all mipmap densities and Android adaptive icons.

## Building an installable APK without Android Studio

1. Push this project to GitHub.
2. Open the **Actions** tab and run **Build BSP-SMS Android APK** (or push changes inside `BSP-SMS-Android/`).
3. Download the `bsp-sms-debug-apk` artifact (`app-debug.apk`) and install it on an Android 8.0+ (API 26+) phone.
