## 3.4\. Web Compatibility

### 3.4.1\. WebView Compatibility

If device implementations provide a complete implementation of the
`android.webkit.Webview` API, they:

*    [C-1-1] MUST report `android.software.webview`.
*    [C-1-2] MUST use the [Chromium](http://www.chromium.org/) Project build
     from the upstream Android Open Source Project on the Android
     ANDROID_VERSION branch for the implementation of the
     [`android.webkit.WebView`](
     http://developer.android.com/reference/android/webkit/WebView.html)
     API.
*    [C-1-3] The user agent string reported by the WebView MUST be in this format:

    Mozilla/5.0 (Linux; Android $(VERSION); $(MODEL) Build/$(BUILD); wv)
    AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 $(CHROMIUM_VER) Mobile
    Safari/537.36

    *   The value of the $(VERSION) string MUST be the same as the value for
        android.os.Build.VERSION.RELEASE.
    *   The value of the $(MODEL) string MUST be the same as the value for
        android.os.Build.MODEL.
    *   The value of the $(BUILD) string MUST be the same as the value for
        android.os.Build.ID.
    *   The value of the $(CHROMIUM_VER) string MUST be the version of Chromium
        in the upstream Android Open Source Project.
    *   Device implementations MAY omit Mobile in the user agent string.

*    The WebView component SHOULD include support for as many HTML5 features as
     possible and if it supports the feature SHOULD conform to the
     [HTML5 specification](http://html.spec.whatwg.org/multipage/).

### 3.4.2\. Browser Compatibility

If device implementations include a standalone Browser application for general
web browsing, they:

*    [C-1-1] MUST support each of these APIs associated with
     HTML5:
    *   [application cache/offline operation](
        http://www.w3.org/html/wg/drafts/html/master/browsers.html#offline)
    *   [&lt;video&gt; tag](
        http://www.w3.org/html/wg/drafts/html/master/semantics.html#video)
    *   [geolocation](http://www.w3.org/TR/geolocation-API/)
*    [C-1-2] MUST support the HTML5/W3C [webstorage API](
     http://www.w3.org/TR/webstorage/) and SHOULD support the HTML5/W3C
     [IndexedDB API](http://www.w3.org/TR/IndexedDB/). Note that as the web
     development standards bodies are transitioning to favor IndexedDB over
     webstorage, IndexedDB is expected to become a required component in a
     future version of Android.
*    MAY ship a custom user agent string in the standalone Browser application.
*    SHOULD implement support for as much of [HTML5](
     http://html.spec.whatwg.org/multipage/) as possible on the standalone
     Browser application (whether based on the upstream WebKit Browser
     application or a third-party replacement).

However, If device implementations do not include a standalone Browser
application, they:

*    [C-2-1] MUST still support the public intent patterns as described in
     [section 3.2.3.1](#3_2_3_1_core_application_intents).
