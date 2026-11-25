# Keep JavascriptInterface methods exposed to the Android WebView bridge
-keepclassmembers class com.bspsms.android.ChromeCompatBridge {
    @android.webkit.JavascriptInterface <methods>;
}
