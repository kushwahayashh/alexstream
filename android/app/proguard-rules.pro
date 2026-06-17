# Keep the JS bridge interface — methods are called reflectively from WebView JS.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
